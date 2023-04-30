package com.wangmuy.llmchain

import com.wangmuy.llmchain.callback.BaseCallbackHandler
import com.wangmuy.llmchain.callback.CallbackManager
import com.wangmuy.llmchain.schema.AgentAction
import com.wangmuy.llmchain.schema.AgentFinish
import com.wangmuy.llmchain.schema.LLMResult
import org.junit.Assert.assertEquals
import org.junit.Test

abstract class BaseFakeCallbackHandler(
    var starts: Int = 0,
    var ends: Int = 0,
    var errors: Int = 0,
    var text: Int = 0,
    var ignoreLLM: Boolean = false,
    var ignoreChain: Boolean = false,
    var ignoreAgent: Boolean = false,
    var alwaysVerbose: Boolean = false,
    var chainStarts: Int = 0,
    var chainEnds: Int = 0,
    var llmStarts: Int = 0,
    var llmEnds: Int = 0,
    var llmStreams: Int = 0,
    var toolStarts: Int = 0,
    var toolEnds: Int = 0,
    var agentEnds: Int = 0
): BaseCallbackHandler

class FakeCallbackHandler(): BaseFakeCallbackHandler() {
    override fun alwaysVerbose(): Boolean {
        return alwaysVerbose
    }

    override fun ignoreLLM(): Boolean {
        return ignoreLLM
    }

    override fun ignoreChain(): Boolean {
        return ignoreChain
    }

    override fun ignoreAgent(): Boolean {
        return ignoreAgent
    }

    override fun onLLMStart(serialized: Map<String, Any>, prompts: List<String>, verbose: Boolean) {
        llmStarts += 1
        starts += 1
    }

    override fun onLLMNewToken(token: String, verbose: Boolean) {
        llmStreams += 1
    }

    override fun onLLMEnd(response: LLMResult, verbose: Boolean) {
        llmEnds += 1
        ends += 1
    }

    override fun onLLMError(error: Throwable, verbose: Boolean) {
        errors += 1
    }

    override fun onChainStart(serialized: Map<String, Any>, inputs: Map<String, Any>?, verbose: Boolean) {
        chainStarts += 1
        starts += 1
    }

    override fun onChainEnd(outputs: Map<String, Any>, verbose: Boolean) {
        chainEnds += 1
        ends += 1
    }

    override fun onChainError(error: Throwable, verbose: Boolean) {
        errors += 1
    }

    override fun onToolStart(serialized: Map<String, Any>, inputStr: String, verbose: Boolean) {
        toolStarts += 1
        starts += 1
    }

    override fun onToolEnd(output: String, verbose: Boolean) {
        toolEnds += 1
        ends += 1
    }

    override fun onToolError(error: Throwable, verbose: Boolean) {
        errors += 1
    }

    override fun onText(text: String, verbose: Boolean) {
        this.text += 1
    }

    override fun onAgentFinish(finish: AgentFinish, verbose: Boolean) {
        agentEnds += 1
        ends += 1
    }

    override fun onAgentAction(action: AgentAction, verbose: Boolean) {
        toolStarts += 1
        starts += 1
    }
}

class CallbackTest {
    @Test fun testCallbackManagerNumCalls() {
        val handler = FakeCallbackHandler()
        val verboseHandler = FakeCallbackHandler().also { it.alwaysVerbose = true }
        val manager = CallbackManager(mutableListOf(handler, verboseHandler))
        manager.onLLMStart(emptyMap(), emptyList())
        manager.onLLMEnd(LLMResult(emptyList()))
        manager.onLLMError(Exception())
        manager.onChainStart(mapOf("name" to "foo"), emptyMap())
        manager.onChainEnd(emptyMap())
        manager.onChainError(Exception())
        manager.onToolStart(emptyMap(), "")
        manager.onToolEnd("")
        manager.onToolError(Exception())
        manager.onAgentFinish(AgentFinish(emptyMap(), ""))

        assertEquals(3, verboseHandler.starts)
        assertEquals(4, verboseHandler.ends)
        assertEquals(3, verboseHandler.errors)
        assertEquals(0, handler.starts)
        assertEquals(0, handler.ends)
        assertEquals(0, handler.errors)
    }
}