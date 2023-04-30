package com.wangmuy.llmchain.agent

import com.wangmuy.llmchain.chain.LLMChain
import com.wangmuy.llmchain.prompt.BasePromptTemplate
import com.wangmuy.llmchain.schema.AgentAction
import com.wangmuy.llmchain.schema.AgentFinish
import com.wangmuy.llmchain.schema.BaseAgentAction
import com.wangmuy.llmchain.schema.BaseMessage
import java.util.*

class IntermediateStep(
    val action: AgentAction,
    val observation: String): BaseAgentAction(action.log)

abstract class BaseAgent() {
    open fun returnValues(): List<String> {
        return Collections.singletonList("output")
    }

    open fun getAllowedTools(): List<String>? {
        return null
    }

    abstract fun plan(intermediateSteps: List<com.wangmuy.llmchain.agent.IntermediateStep>, args: Map<String, Any>?): BaseAgentAction

    abstract fun inputKeys(): List<String>?

    open fun returnStoppedResponse(
        earlyStoppingMethod: String,
        intermediateSteps: List<com.wangmuy.llmchain.agent.IntermediateStep>,
        args: Map<String, Any>?): AgentFinish {
        if (earlyStoppingMethod == "force") {
            return AgentFinish(mapOf("output" to "Agent stopped due to iteration limit or time limit."), "")
        } else {
            throw IllegalArgumentException("Got unsupported early_stopping_method `$earlyStoppingMethod`")
        }
    }

    open fun toolRunLoggingArgs(): Map<String, Any> {
        return Collections.emptyMap()
    }
}

abstract class BaseSingleActionAgent(): com.wangmuy.llmchain.agent.BaseAgent() {
}

abstract class BaseMultiActionAgent(): com.wangmuy.llmchain.agent.BaseAgent() {
}

abstract class Agent @JvmOverloads constructor(
    val llmChain: LLMChain,
    private val allowedTools: List<String>? = null
): com.wangmuy.llmchain.agent.BaseSingleActionAgent() {
    companion object {
        const val AGENT_SCRATCHPAD = "agent_scratchpad"
    }

    override fun getAllowedTools(): List<String>? {
        return allowedTools
    }

    abstract fun extractToolAndInput(text: String): Pair<String, String>?

    protected fun fixText(text: String): String {
        throw NotImplementedError("fix_text not implemented for this agent.")
    }

    protected fun getStop(): List<String> {// _stop
        return listOf(
            "\n${observationPrefix().trimEnd()}",
            "\n\t${observationPrefix().trimEnd()}"
        )
    }

    private fun constructScratchpad(intermediateSteps: List<com.wangmuy.llmchain.agent.IntermediateStep>): String {
        val thoughtsSb = StringBuilder()
        for (step in intermediateSteps) {
            thoughtsSb.append(step.action.log)
            thoughtsSb.append("\n${observationPrefix()}${step.observation}\n${llmPrefix()}")
        }
        return thoughtsSb.toString()
    }

    private fun getNextAction(fullInputs: Map<String, Any>): AgentAction {
        var fullOutput = llmChain.predict(fullInputs)
        var parsedOutput = extractToolAndInput(fullOutput)
        while (parsedOutput == null) {
            fullOutput = fixText(fullOutput)
            val agentScratchpad = fullInputs[com.wangmuy.llmchain.agent.Agent.Companion.AGENT_SCRATCHPAD] as MutableList<BaseMessage>
            agentScratchpad.add(BaseMessage(fullOutput))
            val output = llmChain.predict(fullInputs)
            fullOutput += output
            parsedOutput = extractToolAndInput(fullOutput)
        }
        return AgentAction(parsedOutput.first, parsedOutput.second, fullOutput)
    }

    override fun plan(intermediateSteps: List<com.wangmuy.llmchain.agent.IntermediateStep>, args: Map<String, Any>?)
    : BaseAgentAction {
        val fullInputs = getFullInputs(intermediateSteps, args)
        val action = getNextAction(fullInputs)
        if (action.tool == finishToolName()) {
            return AgentFinish(mapOf("output" to action.toolInput), action.log)
        }
        return action
    }

    private fun getFullInputs(intermediateSteps: List<com.wangmuy.llmchain.agent.IntermediateStep>, args: Map<String, Any>?)
            : Map<String, Any> {
        val thoughts = constructScratchpad(intermediateSteps)
        val newInputs = mapOf<String, Any>(com.wangmuy.llmchain.agent.Agent.Companion.AGENT_SCRATCHPAD to thoughts, "stop" to getStop())
        return args?.toMutableMap()?.also { it.putAll(newInputs) } ?: newInputs
    }

    fun finishToolName(): String {
        return "Final Answer"
    }

    override fun inputKeys(): List<String>? {
        return llmChain.inputKeys()?.filter { it != com.wangmuy.llmchain.agent.Agent.Companion.AGENT_SCRATCHPAD }
    }

    abstract fun observationPrefix(): String
    abstract fun llmPrefix(): String

    // create_prompt
    abstract class PromptBuilder<T: com.wangmuy.llmchain.agent.Agent.PromptBuilder<T, R>, R: BasePromptTemplate> {
        abstract fun self(): T
        abstract fun build(): R
    }

    // from_llm_and_tools
    abstract class Builder<T: com.wangmuy.llmchain.agent.Agent.Builder<T, R>, R> {
        abstract fun self(): T
        abstract fun build(): R
    }

    override fun returnStoppedResponse(
        earlyStoppingMethod: String,
        intermediateSteps: List<com.wangmuy.llmchain.agent.IntermediateStep>,
        args: Map<String, Any>?
    ): AgentFinish {
        when (earlyStoppingMethod) {
            "force" -> {
                return AgentFinish(mapOf("output" to "Agent stopped due to iteration limit or time limit."), "")
            }
            "generate" -> {
                val thoughtsSb = StringBuilder()
                for (step in intermediateSteps) {
                    thoughtsSb.append(step.action.log)
                    thoughtsSb.append("\n${observationPrefix()}${step.observation}${llmPrefix()}")
                }
                thoughtsSb.append("\n\nI now need to return a final answer based on the previous steps:")
                val newInputs = mutableMapOf(com.wangmuy.llmchain.agent.Agent.Companion.AGENT_SCRATCHPAD to thoughtsSb.toString(), "stop" to getStop())
                val fullInputs = newInputs.also {
                    if (args != null) {
                        it.putAll(args)
                    }
                }
                val fullOutput = llmChain.predict(fullInputs)
                val parsedOutput = extractToolAndInput(fullOutput)
                    ?: return AgentFinish(mapOf("output" to fullOutput), fullOutput)
                val tool = parsedOutput.first
                val toolInput = parsedOutput.second
                return if (tool == finishToolName()) {
                    AgentFinish(mapOf("output" to toolInput), fullOutput)
                } else {
                    AgentFinish(mapOf("output" to fullOutput), fullOutput)
                }
            }
            else -> {
                throw IllegalArgumentException("early_stopping_method should be one of `force` or `generate`, " +
                        "got $earlyStoppingMethod")
            }
        }
    }
}