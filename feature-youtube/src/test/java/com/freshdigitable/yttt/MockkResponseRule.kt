package com.freshdigitable.yttt

import io.mockk.MockKMatcherScope
import io.mockk.MockKStubScope
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.every
import org.junit.rules.Verifier

class MockkResponseRule : Verifier() {
    private val registered = mutableListOf<MockKMatcherScope.() -> Any>()
    private val coRegistered = mutableListOf<suspend MockKMatcherScope.() -> Any>()
    private val mocks = mutableListOf<Any>()

    fun addMocks(vararg mock: Any) {
        mocks.addAll(mock)
    }

    fun <R> register(func: MockKMatcherScope.() -> R): MockKStubScope<R, R> {
        @Suppress("UNCHECKED_CAST")
        registered.add(func as MockKMatcherScope.() -> Any)
        return every { func() }
    }

    fun <R> coRegister(func: suspend MockKMatcherScope.() -> R): MockKStubScope<R, R> {
        @Suppress("UNCHECKED_CAST")
        coRegistered.add(func as suspend MockKMatcherScope.() -> Any)
        return coEvery { func() }
    }

    override fun verify() {
        super.verify()
        registered.forEach { io.mockk.verify { it() } }
        coRegistered.forEach { coVerify { it() } }
        confirmVerified(*(mocks.toTypedArray()))
    }
}
