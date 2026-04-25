package gdscript.psi.utils

import com.intellij.psi.PsiElement
import com.intellij.util.containers.tail
import gdscript.psi.impl.*

object GdPsiDictParser {

    fun parse(dictDecl: GdDictDeclImpl): Map<String, Any> {
        val map = mutableMapOf<String, Any>()

        val keyValues = dictDecl.children.filterIsInstance<GdKeyValueImpl>()

        for (kv in keyValues) {
            val key = extractKey(kv) ?: continue
            val value = extractValue(kv)
            value?.let { map[key] = it }
        }

        return map
    }

    private fun extractKey(kv: GdKeyValueImpl): String? {
        val literalKey = (kv.children.firstOrNull { it is GdLiteralExImpl } as? GdLiteralExImpl) ?: return null
        val keyNode = (literalKey.children.firstOrNull() as? GdStringValRefImpl) ?: return null

        return keyNode.text.trim('"')
    }

    private fun extractValue(kv: GdKeyValueImpl): Any? {
        val valueElement = kv.children.lastOrNull() ?: return null

        return when (valueElement) {
            is GdLiteralExImpl -> parseLiteral(valueElement)
            is GdPrimaryExImpl -> parsePrimary(valueElement)
            else -> null
        }
    }

    private fun parseLiteral(literal: GdLiteralExImpl): Any {
        val text = literal.text.trim()

        text.toIntOrNull()?.let { return it }
        text.toDoubleOrNull()?.let { return it }

        val strNode = literal.children.firstOrNull() as? GdStringValRefImpl
        return strNode?.text?.trim('"') ?: text
    }

    private fun parsePrimary(primary: GdPrimaryExImpl): Any? {
        val firstChild = primary.firstChild ?: return null

        return when (firstChild) {
            is GdArrayDeclImpl -> parseArray(firstChild)
            is GdDictDeclImpl -> parse(firstChild)
            else -> parseLiteralLike(primary)
        }
    }

    private fun parseArray(arrayDecl: GdArrayDeclImpl): List<Any> {
        val elements = mutableListOf<Any>()
        for (child in arrayDecl.children) {
            when (child) {
                is GdLiteralExImpl -> elements += parseLiteral(child)
                is GdPrimaryExImpl -> elements += parsePrimary(child) ?: continue
            }
        }

        return elements
    }

    private fun parseLiteralLike(primary: PsiElement): Any {
        val txt = primary.text.trim('"', ' ')
        txt.toIntOrNull()?.let { return it }
        txt.toDoubleOrNull()?.let { return it }

        return txt
    }

    fun resolvePath(path: List<String>, root: Map<String, Any>): Any? {
        if (path.isEmpty() || root.isEmpty()) return null

        val current = path.first()
        val resolved = root[current]

        if (path.size == 1 && resolved != null) {
            return root[current]
        }
        
        if (resolved != null && resolved is Map<*, *>) {
            return resolvePath(path.tail(), resolved as Map<String, Any>)
        }

        return null
    }


    fun resolveReference(path: String, root: Map<String, Any>): Any? {
        var current: Any? = root
        for (segment in path.split('.')) {
            current = when (current) {
                is Map<*, *> -> current[segment]

                is List<*> -> {
                    val idx = segment.toIntOrNull()
                    if (idx != null && idx in current.indices) current[idx] else null
                }

                else -> return null
            }
            if (current == null) return null
        }
        return current
    }

    fun fromElement(element: PsiElement): Map<String, Any>? {
        val dict = (element as? GdDictDeclImpl) ?: return null

        return parse(dict)
    }
}