package gdscript

import com.intellij.lang.*
import com.intellij.lang.ParserDefinition.SpaceRequirements
import com.intellij.lexer.Lexer
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.stubs.PsiFileStub
import com.intellij.psi.tree.*
import com.jetbrains.rider.godot.community.gdscript.GdLanguage
import gdscript.parser.GdRootParser
import gdscript.psi.GdFile
import gdscript.psi.GdTypes

class GdParserDefinition : ParserDefinition {
    companion object {
        val FILE = IStubFileElementType<PsiFileStub<GdFile>>("GdScriptFile", GdLanguage)
    }

    override fun createLexer(project: Project): Lexer {
        return GdLexerAdapter()
    }

    override fun getCommentTokens(): TokenSet {
        return TokenSet.create(GdTypes.COMMENT, GdTypes.BACKSLASH)
    }

    override fun getStringLiteralElements(): TokenSet {
        return TokenSet.create(GdTypes.STRING)
    }

    override fun createParser(project: Project): PsiParser {
        return GdRootParser()
    }

    override fun getFileNodeType(): IFileElementType {
        return FILE
    }

    override fun createFile(viewProvider: FileViewProvider): PsiFile {
        return GdFile(viewProvider)
    }

    override fun spaceExistenceTypeBetweenTokens(left: ASTNode, right: ASTNode): SpaceRequirements {
        return SpaceRequirements.MAY
    }

    override fun createElement(node: ASTNode): PsiElement {
        return GdTypes.Factory.createElement(node)
    }

}
