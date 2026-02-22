package com.freshdigitable.yttt.test

sealed interface Json {
    companion object {
        fun obj(body: Base.() -> Unit): Obj {
            val map = Base()
            body(map)
            return Obj(map.map)
        }
    }

    @JvmInline
    value class Base(val map: MutableMap<String, Any> = mutableMapOf()) : Json {
        operator fun set(key: String, value: Any?) {
            when (value) {
                null -> return
                is String -> map[key] = Str(value)
                is Number -> map[key] = Num(value)
                is Boolean -> map[key] = Bool(value)
                is Collection<*> -> map[key] = Arr(value.filterIsInstance<Json>())
                is Json -> map[key] = value
                else -> throw AssertionError()
            }
        }
    }

    @JvmInline
    value class Obj(val map: Map<String, Any>) : Json {
        override fun toString(): String = map.entries.joinToString(",", "{", "}") { "\"${it.key}\":${it.value}" }
    }

    @JvmInline
    value class Arr(val list: List<Json>) : Json {
        override fun toString(): String = list.joinToString(",", "[", "]")
    }

    @JvmInline
    value class Str(val str: String) : Json {
        override fun toString(): String = "\"$str\""
    }

    @JvmInline
    value class Num(val num: Number) : Json {
        override fun toString(): String = "$num"
    }

    @JvmInline
    value class Bool(val bool: Boolean) : Json {
        override fun toString(): String = "$bool"
    }
}

interface ResponseJson : Json {
    val statusCode: Int get() = 200
}
