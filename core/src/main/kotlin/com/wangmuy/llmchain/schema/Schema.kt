package com.wangmuy.llmchain.schema

open class BaseAgentAction(val log: String)

class AgentAction(
    val tool: String,
    val toolInput: String,
    log: String
): BaseAgentAction(log)

class AgentFinish(
    val returnValues: Map<String, Any>,
    log: String
): BaseAgentAction(log)

open class Generation @JvmOverloads constructor(
    val text: String,
    val generationInfo: Map<String, Any>? = null
)

open class BaseMessage @JvmOverloads constructor(
    val content: String,
    val additionalArgs: Map<String, String>? = null
) {
    open fun msgType(): String {
        return "str"
    }

    override fun toString(): String {
        return content
    }
}

class HumanMessage @JvmOverloads constructor(
    content: String, additionalArgs: Map<String, String>? = null)
    : BaseMessage(content, additionalArgs) {
    override fun msgType(): String {
        return "human"
    }
}

class AIMessage @JvmOverloads constructor(
    content: String, additionalArgs: Map<String, String>? = null)
    : BaseMessage(content, additionalArgs) {
    override fun msgType(): String {
        return "ai"
    }
}

class SystemMessage @JvmOverloads constructor(
    content: String, additionalArgs: Map<String, String>? = null)
    : BaseMessage(content, additionalArgs) {
    override fun msgType(): String {
        return "system"
    }
}

class ChatMessage @JvmOverloads constructor(
    val role: String, content: String, additionalArgs: Map<String, String>? = null)
    : BaseMessage(content, additionalArgs) {
    override fun msgType(): String {
        return "chat"
    }
}

class ChatGeneration(text: String, val message: BaseMessage): Generation(text)

class ChatResult(val generations: List<ChatGeneration>, val llmOutput: Map<String, String>?)

open class LLMResult @JvmOverloads constructor(
        val generations: List<List<Generation>>,
        val llmOutput: Map<String, Any>? = null
)

abstract class PromptValue {
    abstract fun asString(): String
    abstract  fun asMessage(): List<BaseMessage>
}

abstract class BaseLanguageModel {
    abstract fun generatePrompt(
            prompts: List<PromptValue>, stop: List<String>?): LLMResult
}

abstract class BaseMemory {
    abstract fun memoryVariables(): List<String>
    abstract fun loadMemoryVariables(inputs: Map<String, Any>): Map<String, Any>
    abstract fun saveContext(inputs: Map<String, Any>, outputs: Map<String, String>)
    abstract fun clear()
}

abstract class BaseChatMessageHistory @JvmOverloads constructor(
    val messages: MutableList<BaseMessage> = mutableListOf()) {
    open fun addUserMessage(message: String) {
        messages.add(HumanMessage(message))
    }

    open fun addAIMessage(message: String) {
        messages.add(AIMessage(message))
    }

    open fun clear() {
        messages.clear()
    }
}

abstract class Document(val pageContent: String, val metadata: Map<String, Any>)