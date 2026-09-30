package com.tobfd.tsuzuki.core.network.debug

import org.junit.Assert.assertEquals
import org.junit.Test

class RequestLogInterceptorTest {

    @Test
    fun request_showsTheOperationAndItsVariables() {
        val body = """{"operationName":"SearchMedia","variables":{"page":1,"type":"ANIME","isAdult":false},""" +
            """"query":"query SearchMedia { … }"}"""
        assertEquals("""→ SearchMedia {"page":1,"type":"ANIME","isAdult":false}""", describeRequest(body))
    }

    @Test
    fun response_showsGraphQlErrorsThatCameWithHttp200() {
        val body = """{"data":null,"errors":[{"message":"Validation error","status":400}]}"""
        assertEquals(
            """← 200, ${body.length} chars, errors: [{"message":"Validation error","status":400}]""",
            describeResponse(200, body)
        )
    }

    @Test
    fun response_withoutErrors_isOneShortLine() {
        assertEquals("← 200, 11 chars", describeResponse(200, """{"data":{}}"""))
    }
}
