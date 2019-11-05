/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

package org.jetbrains.kotlin.native.interop.gen

import org.jetbrains.kotlin.native.interop.indexer.*

/**
 * Allows to nest declaration mangles.
 */
sealed class ManglingContext {

    abstract val prefix: String

    class Module(name: String) : ManglingContext() {
        override val prefix: String = name
    }

    /**
     * Used to represent containers like structures, classes, etc.
     */
    class Entity(name: String, parentContext: ManglingContext? = null) : ManglingContext() {
        override val prefix: String = run {
            val parent = if (parentContext != null) "${parentContext.prefix}." else ""
            "$parent$name"
        }
    }

    object Empty : ManglingContext() {
        override val prefix: String = ""
    }
}

/**
 * We need a way to refer external declarations from Kotlin Libraries
 * by stable unique identifier. To be able to do it, we mangle them.
 */
interface InteropMangler {
    val StructDecl.uniqueSymbolName: String
    val EnumDef.uniqueSymbolName: String
    val ObjCClass.uniqueSymbolName: String
    val ObjCClass.metaClassUniqueSymbolName: String
    val ObjCProtocol.uniqueSymbolName: String
    val ObjCCategory.uniqueSymbolName: String
    val ObjCMethod.uniqueSymbolName: String
    val ObjCProperty.uniqueSymbolName: String
    val TypedefDef.uniqueSymbolName: String
    val FunctionDecl.uniqueSymbolName: String
    val ConstantDef.uniqueSymbolName: String
    val WrappedMacroDef.uniqueSymbolName: String
    val GlobalDecl.uniqueSymbolName: String
}

/**
 * Mangler that mimics behaviour of the one from the Kotlin compiler.
 */
class KotlinLikeInteropMangler(val context: ManglingContext = ManglingContext.Empty) : InteropMangler {

    private val prefix = context.prefix

    override val StructDecl.uniqueSymbolName: String
        get() = "structdecl:$prefix$spelling"

    override val EnumDef.uniqueSymbolName: String
        get() = "enumdef:$prefix$spelling"

    override val ObjCClass.uniqueSymbolName: String
        get() = "objcclass:$prefix$name"

    override val ObjCClass.metaClassUniqueSymbolName: String
        get() = "objcmetaclass:$prefix$name"

    override val ObjCProtocol.uniqueSymbolName: String
        get() = "objcprotocol:$prefix$name"

    override val ObjCCategory.uniqueSymbolName: String
        get() = "objccategory:$prefix${clazz.name}+$name"

    override val ObjCMethod.uniqueSymbolName: String
        get() = "objcmethod:$prefix$selector$encoding"

    override val ObjCProperty.uniqueSymbolName: String
        get() = "objcproperty:$prefix$name"

    override val TypedefDef.uniqueSymbolName: String
        get() = "typedef:$prefix$name"

    override val FunctionDecl.uniqueSymbolName: String
        get() = "funcdecl:$prefix$functionName$signature"

    override val ConstantDef.uniqueSymbolName: String
        get() = "macrodef:$prefix$name${type.mangle}"

    override val WrappedMacroDef.uniqueSymbolName: String
        get() = "macrodef:$prefix$name${type.mangle}"

    override val GlobalDecl.uniqueSymbolName: String
        get() = "globaldecl:$prefix$name${type.mangle}"

    private val FunctionDecl.functionName: String
        get() = name

    private val FunctionDecl.signature: String
        get() {
            val parameters = parameters.joinToString(separator = ";") { it.type.mangle }
            val vararg = if (isVararg) "..." else ""
            return "($parameters$vararg)${returnType.mangle}"
        }

    // TODO: Looks fragile.
    private val Type.mangle: String
        get() = when (this) {
            VoidType -> "void"
            CharType, is BoolType -> "char"
            is IntegerType -> spelling
            is FloatingType -> spelling
            is RecordType -> decl.spelling
            is EnumType -> def.spelling
            is PointerType -> "${if (pointeeIsConst) "const " else ""}${pointeeType.mangle}*"
            is ConstArrayType -> "${elemType.mangle}[]"
            is IncompleteArrayType -> "${elemType.mangle}[]"
            is Typedef -> def.aliased.mangle
            is ObjCPointer -> this.mangle
            else -> error("Unexpected type $this")
        }

    private val ObjCPointer.mangle: String
        get() = when (this) {
            is ObjCObjectPointer -> "objc:objectptr"
            is ObjCClassPointer -> "objc:classptr"
            is ObjCIdType -> "objc:id"
            is ObjCInstanceType -> "objc:instance"
            is ObjCBlockPointer ->
                "objc:blockptr(${parameterTypes.joinToString(separator = ";") { it.mangle }})${returnType.mangle}"
        }
}