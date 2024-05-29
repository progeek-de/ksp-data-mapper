package de.progeek.mapper

import MapperProcessor
import MapperProcessorProvider
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.symbolProcessorProviders
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals


@ExperimentalCompilerApi
internal class MapperGeneratorTest {
    @Test
    fun `test something`() {
        val source = SourceFile.kotlin("StudentMapper.kt","""
            import de.progeek.mapper.Mapper

            data class StudentEntity {
                val firstName: String
                val lastName: String
                val matNr: String
            }

            data class StudentDTO {
                val firstName: String
                val lastName: String
            }

            @Mapper(generateExtension = true)
            abstract class StudentMapper {
                abstract fun toDTO(entity: StudentEntity): StudentDTO
            }
        """.trimIndent())

        val compilation = KotlinCompilation().apply {
            sources = listOf(source)
            symbolProcessorProviders = listOf(MapperProcessorProvider())
        }

        val result = compilation.compile()

        println(result.generatedFiles)
        println(result.generatedStubFiles)
        println(result.sourcesGeneratedByAnnotationProcessor)

    }
}
