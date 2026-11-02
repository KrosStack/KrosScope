package io.github.krosstack.kscope.benchmark.agent.telemetry

internal object ClaudeFixtures {
    const val INIT_EVENT = """{"type":"system","subtype":"init","tools":["Edit","Glob","Grep","Read","Write"],""" +
        """"mcp_servers":[],"model":"model-x","slash_commands":[],"skills":[],"claude_code_version":"9.9.9"}"""

    const val FINISHED = """{"type":"result","subtype":"success","is_error":false,"duration_ms":1234,""" +
        """"duration_api_ms":900,"num_turns":3,"result":"done","total_cost_usd":0.0123,""" +
        """"usage":{"input_tokens":20,"cache_creation_input_tokens":200,"cache_read_input_tokens":300,""" +
        """"output_tokens":50,"output_tokens_details":{"thinking_tokens":7}}}"""

    const val AUTHENTICATION_RETRY =
        """{"type":"system","subtype":"api_retry","attempt":1,"error_status":401,"error":"authentication_failed"}"""

    const val SYNTHETIC_MESSAGE =
        """{"type":"assistant","message":{"id":"x","model":"<synthetic>","usage":{"input_tokens":0},"content":[]}}"""

    const val AUTHENTICATION_FAILURE =
        """{"type":"result","subtype":"success","is_error":true,"api_error_status":401,"num_turns":1,""" +
            """"total_cost_usd":0,"usage":{"input_tokens":0,"output_tokens":0},""" +
            """"result":"Failed to authenticate. API Error: 401"}"""

    fun lines(vararg text: String) = text.mapIndexed { index, line -> TimedLine(index, index * 100L, line) }

    fun assistant(id: String, content: String, usage: String = """{"input_tokens":1,"output_tokens":1}""") =
        """{"type":"assistant","message":{"id":"$id","model":"model-x","usage":$usage,"content":[$content]}}"""

    fun toolUse(id: String, name: String, path: String) =
        """{"type":"tool_use","id":"$id","name":"$name","input":{"file_path":"$path"}}"""

    fun toolResult(id: String, text: String, error: Boolean = false) =
        """{"type":"user","message":{"content":[{"type":"tool_result","tool_use_id":"$id",""" +
            """"content":"$text","is_error":$error}]}}"""
}
