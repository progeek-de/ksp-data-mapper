import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSVisitorVoid
import com.google.devtools.ksp.validate
import de.progeek.mapper.MapperGenerator

class MapperProcessor(val codeGenerator: CodeGenerator, val logger: KSPLogger) : SymbolProcessor {
    override fun process(resolver: Resolver): List<KSAnnotated> {
        val symbols = resolver.getSymbolsWithAnnotation("de.progeek.mapper.Mapper")

        val ret = symbols.filter {
            it !is KSClassDeclaration || !it.validate()
        }.toList()

        symbols.filter { it is KSClassDeclaration && it.validate() }
            .forEach { it.accept(MapperVisitor(), Unit) }

        return ret
    }

    inner class MapperVisitor : KSVisitorVoid() {
        override fun visitClassDeclaration(classDeclaration: KSClassDeclaration, data: Unit) {
            logger.info("Visiting class declaration: $classDeclaration")

            MapperGenerator(classDeclaration).writeTo(codeGenerator)
        }
    }
}

class MapperProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return MapperProcessor(environment.codeGenerator, environment.logger)
    }
}
