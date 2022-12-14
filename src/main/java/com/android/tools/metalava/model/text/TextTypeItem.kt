/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tools.metalava.model.text

import com.android.tools.metalava.JAVA_LANG_OBJECT
import com.android.tools.metalava.JAVA_LANG_PREFIX
import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.MemberItem
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeParameterItem
import com.android.tools.metalava.model.TypeParameterList
import com.android.tools.metalava.model.TypeParameterListOwner
import java.util.function.Predicate
import kotlin.math.min

const val ASSUME_TYPE_VARS_EXTEND_OBJECT = false

class TextTypeItem(
    val codebase: TextCodebase,
    val type: String
) : TypeItem {

    override fun toString(): String = type

    override fun toErasedTypeString(context: Item?): String {
        return toTypeString(
            outerAnnotations = false,
            innerAnnotations = false,
            erased = true,
            kotlinStyleNulls = false,
            context = context
        )
    }

    override fun toTypeString(
        outerAnnotations: Boolean,
        innerAnnotations: Boolean,
        erased: Boolean,
        kotlinStyleNulls: Boolean,
        context: Item?,
        filter: Predicate<Item>?
    ): String {
        val typeString = toTypeString(type, outerAnnotations, innerAnnotations, erased, context)

        if (innerAnnotations && kotlinStyleNulls && !primitive && context != null) {
            var nullable: Boolean? = AnnotationItem.getImplicitNullness(context)

            if (nullable == null) {
                for (annotation in context.modifiers.annotations()) {
                    if (annotation.isNullable()) {
                        nullable = true
                    } else if (annotation.isNonNull()) {
                        nullable = false
                    }
                }
            }
            when (nullable) {
                null -> return "$typeString!"
                true -> return "$typeString?"
                else -> { /* non-null: nothing to add */ }
            }
        }
        return typeString
    }

    override fun asClass(): ClassItem? {
        if (primitive) {
            return null
        }
        val cls = run {
            val erased = toErasedTypeString()
            // Also chop off array dimensions
            val index = erased.indexOf('[')
            if (index != -1) {
                erased.substring(0, index)
            } else {
                erased
            }
        }
        return codebase.getOrCreateClass(cls)
    }

    fun qualifiedTypeName(): String = type

    override fun equals(other: Any?): Boolean {
        if (this === other) return true

        return when (other) {
            // Note: when we support type-use annotations, this is not safe: there could be a string
            // literal inside which is significant
            is TextTypeItem -> TypeItem.equalsWithoutSpace(toString(), other.toString())
            is TypeItem -> {
                val thisString = toTypeString()
                val otherString = other.toTypeString()
                if (TypeItem.equalsWithoutSpace(thisString, otherString)) {
                    return true
                }
                if (thisString.startsWith(JAVA_LANG_PREFIX) && thisString.endsWith(otherString) &&
                    thisString.length == otherString.length + JAVA_LANG_PREFIX.length
                ) {
                    // When reading signature files, it's sometimes ambiguous whether a name
                    // references a java.lang. implicit class or a type parameter.
                    return true
                }

                return false
            }
            else -> false
        }
    }

    override fun hashCode(): Int {
        return qualifiedTypeName().hashCode()
    }

    override fun arrayDimensions(): Int {
        val type = toErasedTypeString()
        var dimensions = 0
        for (c in type) {
            if (c == '[') {
                dimensions++
            }
        }
        return dimensions
    }

    private fun findTypeVariableBounds(typeParameterList: TypeParameterList, name: String): List<TypeItem> {
        for (p in typeParameterList.typeParameters()) {
            if (p.simpleName() == name) {
                val bounds = p.typeBounds()
                if (bounds.isNotEmpty()) {
                    return bounds
                }
            }
        }

        return emptyList()
    }

    private fun findTypeVariableBounds(context: Item?, name: String): List<TypeItem> {
        if (context is MethodItem) {
            val bounds = findTypeVariableBounds(context.typeParameterList(), name)
            if (bounds.isNotEmpty()) {
                return bounds
            }
            return findTypeVariableBounds(context.containingClass().typeParameterList(), name)
        } else if (context is ClassItem) {
            return findTypeVariableBounds(context.typeParameterList(), name)
        }

        return emptyList()
    }

    override fun asTypeParameter(context: MemberItem?): TypeParameterItem? {
        return if (isLikelyTypeParameter(toTypeString())) {
            val typeParameter =
                TextTypeParameterItem.create(codebase, context as? TypeParameterListOwner, toTypeString())

            if (context != null && typeParameter.typeBounds().isEmpty()) {
                val bounds = findTypeVariableBounds(context, typeParameter.simpleName())
                if (bounds.isNotEmpty()) {
                    val filtered = bounds.filter { !it.isJavaLangObject() }
                    if (filtered.isNotEmpty()) {
                        return TextTypeParameterItem.create(
                            codebase,
                            context as? TypeParameterListOwner,
                            toTypeString(),
                            bounds
                        )
                    }
                }
            }

            typeParameter
        } else {
            null
        }
    }

    override val primitive: Boolean
        get() = isPrimitive(type)

    override fun typeArgumentClasses(): List<ClassItem> = codebase.unsupported()

    override fun convertType(replacementMap: Map<String, String>?, owner: Item?): TypeItem {
        return TextTypeItem(codebase, convertTypeString(replacementMap))
    }

    override fun markRecent() = codebase.unsupported()

    override fun scrubAnnotations() = codebase.unsupported()

    companion object {
        // heuristic to guess if a given type parameter is a type variable
        fun isLikelyTypeParameter(typeString: String): Boolean {
            val first = typeString[0]
            if (!Character.isUpperCase((first)) && first != '_') {
                // This rules out primitives which otherwise don't have
                return false
            }
            for (c in typeString) {
                if (c == '.') {
                    // This rules out qualified class names
                    return false
                }
                if (c == ' ' || c == '[' || c == '<') {
                    return true
                }
                // I'd like to check for all uppercase here but there are APIs which
                // violate this, such as AsyncTask where the type variable names include "Result"
            }

            return true
        }

        fun toTypeString(
            type: String,
            outerAnnotations: Boolean,
            innerAnnotations: Boolean,
            erased: Boolean,
            context: Item? = null
        ): String {
            return if (erased) {
                val raw = eraseTypeArguments(type)
                val concrete = eraseTypeArguments(substituteTypeParameters(raw, context))
                if (outerAnnotations && innerAnnotations) {
                    concrete
                } else {
                    eraseAnnotations(concrete, outerAnnotations, innerAnnotations)
                }
            } else {
                if (outerAnnotations && innerAnnotations) {
                    type
                } else {
                    eraseAnnotations(type, outerAnnotations, innerAnnotations)
                }
            }
        }

        private fun substituteTypeParameters(s: String, context: Item?): String {
            if (context is TypeParameterListOwner) {
                var end = s.indexOf('[')
                if (end == -1) {
                    end = s.length
                }
                if (s[0].isUpperCase() && s.lastIndexOf('.', end) == -1) {
                    val v = s.substring(0, end)
                    val parameter = context.resolveParameter(v)
                    if (parameter != null) {
                        val bounds = parameter.typeBounds()
                        if (bounds.isNotEmpty()) {
                            return bounds.first().toTypeString() + s.substring(end)
                        }
                        @Suppress("ConstantConditionIf")
                        if (ASSUME_TYPE_VARS_EXTEND_OBJECT) {
                            return JAVA_LANG_OBJECT + s.substring(end)
                        }
                    }
                }
            }

            return s
        }

        fun eraseTypeArguments(s: String): String {
            val index = s.indexOf('<')
            if (index != -1) {
                var balance = 0
                for (i in index..s.length) {
                    val c = s[i]
                    if (c == '<') {
                        balance++
                    } else if (c == '>') {
                        balance--
                        if (balance == 0) {
                            return if (i == s.length - 1) {
                                s.substring(0, index)
                            } else {
                                s.substring(0, index) + s.substring(i + 1)
                            }
                        }
                    }
                }

                return s.substring(0, index)
            }
            return s
        }

        /**
         * Given a type possibly using the Kotlin-style null syntax, strip out any Kotlin-style
         * null syntax characters, e.g. "String?" -> "String", but make sure not to damage types
         * like "Set<? extends Number>".
         */
        fun stripKotlinNullChars(s: String): String {
            var found = false
            var prev = ' '
            for (c in s) {
                if (c == '!' || c == '?' && (prev != '<' && prev != ',' && prev != ' ')) {
                    found = true
                    break
                }
                prev = c
            }

            if (!found) {
                return s
            }

            val sb = StringBuilder(s.length)
            for (c in s) {
                if (c == '!' || c == '?' && (prev != '<' && prev != ',' && prev != ' ')) {
                    // skip
                } else {
                    sb.append(c)
                }
                prev = c
            }

            return sb.toString()
        }

        private fun eraseAnnotations(type: String, outer: Boolean, inner: Boolean): String {
            if (type.indexOf('@') == -1) {
                // If using Kotlin-style null syntax, strip those markers as well
                return stripKotlinNullChars(type)
            }

            assert(inner || !outer) // Can't supply outer=true,inner=false

            // Assumption: top level annotations appear first
            val length = type.length
            var max = if (!inner)
                length
            else {
                val space = type.indexOf(' ')
                val generics = type.indexOf('<')
                val first = if (space != -1) {
                    if (generics != -1) {
                        min(space, generics)
                    } else {
                        space
                    }
                } else {
                    generics
                }
                if (first != -1) {
                    first
                } else {
                    length
                }
            }

            var s = type
            while (true) {
                val index = s.indexOf('@')
                if (index == -1 || index >= max) {
                    break
                }

                // Find end
                val end = findAnnotationEnd(s, index + 1)
                val oldLength = s.length
                s = s.substring(0, index).trim() + s.substring(end).trim()
                val newLength = s.length
                val removed = oldLength - newLength
                max -= removed
            }

            // Sometimes we have a second type after the max, such as
            // @androidx.annotation.NonNull java.lang.reflect.@androidx.annotation.NonNull TypeVariable<...>
            for (i in s.indices) {
                val c = s[i]
                if (Character.isJavaIdentifierPart(c) || c == '.') {
                    continue
                } else if (c == '@') {
                    // Found embedded annotation within the type
                    val end = findAnnotationEnd(s, i + 1)
                    if (end == -1 || end == length) {
                        break
                    }

                    s = s.substring(0, i).trim() + s.substring(end).trim()
                    break
                } else {
                    break
                }
            }

            return s
        }

        private fun findAnnotationEnd(type: String, start: Int): Int {
            var index = start
            val length = type.length
            var balance = 0
            while (index < length) {
                val c = type[index]
                if (c == '(') {
                    balance++
                } else if (c == ')') {
                    balance--
                    if (balance == 0) {
                        return index + 1
                    }
                } else if (c == '.') {
                } else if (Character.isJavaIdentifierPart(c)) {
                } else if (balance == 0) {
                    break
                }
                index++
            }
            return index
        }

        fun isPrimitive(type: String): Boolean {
            return when (type) {
                "byte", "char", "double", "float", "int", "long", "short", "boolean", "void", "null" -> true
                else -> false
            }
        }
    }
}
