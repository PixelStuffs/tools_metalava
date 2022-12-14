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

package com.android.tools.metalava.model.visitors

import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.ConstructorItem
import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.PackageItem
import com.android.tools.metalava.model.ParameterItem
import com.android.tools.metalava.model.PropertyItem
import com.android.tools.metalava.model.SourceFileItem

open class ItemVisitor(
    /**
     * Whether constructors should be visited as part of a [#visitMethod] call
     * instead of just a [#visitConstructor] call. Helps simplify visitors that
     * don't care to distinguish between the two cases. Defaults to true.
     */
    val visitConstructorsAsMethods: Boolean = true,
    /**
     * Whether inner classes should be visited "inside" a class; when this property
     * is true, inner classes are visited before the [#afterVisitClass] method is
     * called; when false, it's done afterwards. Defaults to false.
     */
    val nestInnerClasses: Boolean = false,
    /**
     * Whether to skip empty packages
     */
    val skipEmptyPackages: Boolean = false
) {

    open fun skip(item: Item): Boolean = false

    /** Visits the item. This is always called before other more specialized visit methods, such as [visitClass]. */
    open fun visitItem(item: Item) {}

    open fun visitCodebase(codebase: Codebase) {}
    open fun visitPackage(pkg: PackageItem) {}
    open fun visitSourceFile(sourceFile: SourceFileItem) {}
    open fun visitClass(cls: ClassItem) {}
    open fun visitConstructor(constructor: ConstructorItem) {
        if (visitConstructorsAsMethods) {
            visitMethod(constructor)
        }
    }

    open fun visitField(field: FieldItem) {}
    open fun visitMethod(method: MethodItem) {}
    open fun visitParameter(parameter: ParameterItem) {}
    open fun visitProperty(property: PropertyItem) {}

    open fun afterVisitItem(item: Item) {}
    open fun afterVisitCodebase(codebase: Codebase) {}
    open fun afterVisitPackage(pkg: PackageItem) {}
    open fun afterVisitSourceFile(sourceFile: SourceFileItem) {}
    open fun afterVisitClass(cls: ClassItem) {}
    open fun afterVisitConstructor(constructor: ConstructorItem) {
        if (visitConstructorsAsMethods) {
            afterVisitMethod(constructor)
        }
    }

    open fun afterVisitField(field: FieldItem) {}
    open fun afterVisitMethod(method: MethodItem) {}
    open fun afterVisitParameter(parameter: ParameterItem) {}
    open fun afterVisitProperty(property: PropertyItem) {}
}
