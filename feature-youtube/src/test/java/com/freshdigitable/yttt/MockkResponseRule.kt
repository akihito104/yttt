package com.freshdigitable.yttt

import io.mockk.MockKMatcherScope
import io.mockk.MockKStubScope
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.junit4.MockKRule
import org.junit.rules.RuleChain
import org.junit.rules.Verifier
import org.junit.runner.Description
import org.junit.runners.model.Statement

class MockkResponseRule(testSubject: Any) : Verifier() {
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

    private val mockk = MockKRule(testSubject)
    override fun apply(base: Statement?, description: Description?): Statement =
        RuleChain.outerRule(mockk).apply(super.apply(base, description), description)

    override fun verify() {
        registered.forEach { io.mockk.verify { it() } }
        coRegistered.forEach { coVerify { it() } }
        confirmVerified(*(mocks.toTypedArray()))
    }
}
