package de.progeek.mapper

import com.google.devtools.ksp.*
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ksp.addOriginatingKSFile
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.writeTo

@OptIn(KspExperimental::class)
class MapperGenerator(private val classDeclaration: KSClassDeclaration) {
    private val annotation = classDeclaration.getAnnotationsByType(Mapper::class).first()
    private val packageName = classDeclaration.packageName.asString()
    private val parentName = classDeclaration.simpleName.asString()
    private val className = "${parentName}Impl"

    private val mapperFunctions = classDeclaration.getAllFunctions().toList().associateBy {
        it.simpleName.asString()
    }

    private val dependencies: MutableList<KSFile> = mutableListOf(
        classDeclaration.containingFile!!
    )

    private val extensionFunctions: MutableList<FunSpec> = mutableListOf()

    fun writeTo(codeGenerator: CodeGenerator) {
        val file = buildMapperFile()
        file.writeTo(
            codeGenerator, Dependencies(
                true,
                *dependencies.toTypedArray()
            )
        )
    }

    private fun buildMapperFile(): FileSpec {
        val file = FileSpec.builder(packageName, className)
            .addType(buildMapperClass())

        extensionFunctions.forEach {
            file.addFunction(it)
        }

        return file.build()
    }

    private fun buildMapperClass(): TypeSpec {
        val mapper =
            if (classDeclaration.primaryConstructor!!.parameters.isNotEmpty()) TypeSpec.classBuilder(className) else TypeSpec.objectBuilder(
                className
            )

        mapper.superclass(classDeclaration.toClassName())
            .addOriginatingKSFile(classDeclaration.containingFile!!)

        classDeclaration.getDeclaredFunctions().filter {
            it.isAbstract && it.parameters.isNotEmpty() && it.returnType !== null
        }.forEach { func ->
            mapper.addFunction(buildMapperFunction(func))
            if (annotation.generateExtension) {
                extensionFunctions.add(buildExtensionFunction(func))
            }
        }

        classDeclaration.primaryConstructor?.let { constructor ->
            if (constructor.parameters.isNotEmpty()) {
                val params = constructor.parameters.map {
                    ParameterSpec.builder(it.name!!.asString(), it.type.resolve().toClassName()).build()
                }

                mapper.primaryConstructor(
                    FunSpec.constructorBuilder().addParameters(params).build()
                )

                mapper.addSuperclassConstructorParameter(params.joinToString(",") { it.name })
            }
        }

        return mapper.build()
    }

    private fun buildMapperFunction(abstractDeclaration: KSFunctionDeclaration): FunSpec {
        val source = abstractDeclaration.parameters.first().type
        val sourceName = abstractDeclaration.parameters.first().name
        val target = abstractDeclaration.returnType!!

        val sourceType = source.resolve()
        val targetType = target.resolve()

        dependencies.add(source.containingFile!!)
        dependencies.add(target.containingFile!!)

        return FunSpec.builder(abstractDeclaration.simpleName.asString())
            .addParameters(abstractDeclaration.parameters.map {
                ParameterSpec(it.name!!.asString(), it.type.resolve().toClassName())
            })
            .addModifiers(KModifier.OVERRIDE)
            .addCode(buildMapperCode(sourceType, sourceName!!, targetType, abstractDeclaration))
            .returns(targetType.toClassName())
            .build()
    }

    private fun buildMapperCode(source: KSType, sourceName: KSName, target: KSType, abstractDeclaration: KSFunctionDeclaration): CodeBlock {
        val code = CodeBlock.builder()
        val constructor = (target.declaration as KSClassDeclaration).primaryConstructor


        val initCode = constructor?.parameters?.mapNotNull {
            buildParameterStatement(source, sourceName, it, abstractDeclaration)?.toString()
        }?.joinToString("\n")

        return code.addStatement("return %T(\n$initCode\n)", target.toClassName()).build()
    }

    private fun buildParameterStatement(
        source: KSType,
        sourceName: KSName,
        param: KSValueParameter,
        abstractDeclaration: KSFunctionDeclaration
    ): CodeBlock? {
        val paramName = param.name!!.asString()
        val sourceFunctions = (source.declaration as KSClassDeclaration).getDeclaredFunctions().toList().associateBy {
            it.simpleName.asString()
        }

        val sourceParams = (source.declaration as KSClassDeclaration).getDeclaredProperties().toList().associateBy {
            it.simpleName.asString()
        }

        val abstractParameters = abstractDeclaration.parameters.associateBy { it.name!!.asString() }

        val invoker = if (abstractParameters.containsKey(paramName)) {
            paramName
        } else if (mapperFunctions.containsKey(paramName)) {
            val fn = mapperFunctions[paramName]!!
            if (fn.parameters.isNotEmpty()) {
                "$paramName(${sourceName.asString()})"
            } else {
                "$paramName()"
            }
        } else if (sourceFunctions.containsKey(paramName)) {
            "${sourceName.asString()}.$paramName()"
        } else if (sourceParams.containsKey(paramName)){
            "${sourceName.asString()}.${paramName}"
        } else {
            return null
        }

        return CodeBlock.of("$paramName = $invoker,")
    }

    private fun buildExtensionFunction(abstractDeclaration: KSFunctionDeclaration): FunSpec {
        val source = abstractDeclaration.parameters.first().type.resolve()
        val target = abstractDeclaration.returnType!!.resolve()

        val funcName = abstractDeclaration.simpleName.asString()
        val func = FunSpec.builder(funcName)
            .receiver(source.toClassName())
            .returns(target.toClassName())

        val params = abstractDeclaration.parameters
            .filter { it.type.resolve() !== source }
            .map {
                ParameterSpec(it.name!!.asString(), it.type.resolve().toClassName())
            }

        func.addParameters(params)

        val callParams = (listOf("this") + params.map { it.name }).joinToString(",")

        if (classDeclaration.primaryConstructor!!.parameters.isNotEmpty()) {
            func.addStatement("return $className().$funcName($callParams)")
        } else {
            func.addStatement("return $className.$funcName($callParams)")
        }


        return func.build()
    }
}
