package com.example.kotlin.controller

import io.opentracing.Tracer
import kotlinx.coroutines.*
import org.springframework.stereotype.Repository
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ServerErrorException
import kotlin.coroutines.CoroutineContext

@RestController
class SampleController(val service: SampleService, val tracer: Tracer) {
    @GetMapping("/")
    suspend fun index(): SampleEntity {
        println("index() current thread name = ${Thread.currentThread().name}")
        return service.findEntity()
    }

    @GetMapping("/async")
    suspend fun asyncRequest(): SampleEntity = coroutineScope {
        println("asyncRequest() current thread name = ${Thread.currentThread().name}")

        val name = async {
            service.getName()
        }
        val age = async {
            service.getAge()
        }

        SampleEntity(name.await(), age.await())
    }

    /**
     * 같은 코루틴 스콥에서 서로 다른 비동기 작업처리
     */
    @GetMapping("/async2")
    suspend fun asyncRequest2(): SampleEntity {
        println("asyncRequest2() current thread name = ${Thread.currentThread().name}")

        val scope = CoroutineScope(Dispatchers.IO)

        val name = scope.async {
            service.getName()
        }
        val age = scope.async {
            service.getAge()
        }

        return SampleEntity(name = name.await(), age = age.await())
    }

    /**
     * 다른 코루틴 스콥에서 서로 다른 비동기 작업처리.
     * 각 비동기 작업은 다른 코루틴에서 실행된다.
     */
    @GetMapping("/async3")
    suspend fun asyncRequest3(): SampleEntity {
        println("asyncRequest2() current thread name = ${Thread.currentThread().name}")

        val name = CoroutineScope(Dispatchers.IO).async {
            service.getName()
        }
        val age = CoroutineScope(Dispatchers.IO).async {
            service.getAge()
        }

        return SampleEntity(name = name.await(), age = age.await())
    }

    @GetMapping("/launch")
    suspend fun launchRequest(): SampleEntity {
        CoroutineScope(Dispatchers.IO).launch {
            println("launchRequest() current thread name = ${Thread.currentThread().name}")
        }

        return service.findEntity()
    }

    /**
     * await()은 예외를 그대로 전파한다.
     */
    @GetMapping("/exception")
    suspend fun exceptionHandle(): SampleEntity = try {
        val result = CoroutineScope(Dispatchers.IO).async {
            doSomething()
        }
        result.await()
    } catch (e: UnsupportedOperationException) {
        SampleEntity(name = "default", age = 100)
    }

    /**
     * join()을 하면 예외를 전파하는 것이 아니고, 잡아서 처리한다.
     */
    @GetMapping("/exception2")
    suspend fun exceptionHandle2(): SampleEntity {
        val result = CoroutineScope(Dispatchers.IO).async {
            doSomething()
        }

        //비동기 잡이 처리되기까지 대기한다.
        result.join()

        return if (result.isCancelled) {
            SampleEntity(name = "default", age = 100)
        } else {
            result.await()
        }
    }

    @GetMapping("/exception3")
    suspend fun exceptionHandle3(): SampleEntity = try {
        val result = CoroutineScope(Dispatchers.IO).async {
            doSomething()
        }
        result.await()
    } catch (e: UnsupportedOperationException) {
        throw ServerErrorException("internal server error", e)
    }

    @GetMapping("/exception4")
    suspend fun exceptionHandle4(): SampleEntity {
        val result = CoroutineScope(Dispatchers.IO).async {
            doSomething()
        }

        return result.await()
    }

    @GetMapping("/exception5")
    suspend fun exceptionHandle5(): SampleEntity {
        val exceptionHandler = CoroutineExceptionHandler {
            _: CoroutineContext, throwable: Throwable ->
                println("Job cancelled due to ${throwable.message}")
        }

        val result = CoroutineScope(Dispatchers.IO).async(exceptionHandler) {
            doSomething()
        }

        return result.await()
    }

    @GetMapping("/exception6")
    suspend fun exceptionHandle6(): SampleEntity {
        CoroutineScope(Dispatchers.IO).async {
            doSomething()
        }.invokeOnCompletion { throwable ->
            throwable?.let {
                println("Job cancelled due to ${throwable.message}")
            }
        }

        return SampleEntity(name = "default", age = 100)
    }

    private suspend fun doSomething(): SampleEntity {
        throw UnsupportedOperationException("unsupported operation exception")
    }
}

@Service
class SampleService(val repository: SampleRepository) {
    suspend fun findEntity(): SampleEntity {
        println("findEntity() current thread name = ${Thread.currentThread().name}")
        return repository.findSample()
    }

    suspend fun getName(): String {
        println("getName() current thread name = ${Thread.currentThread().name}")
        return "sora"
    }

    suspend fun getAge(): Int {
        println("getAge() current thread name = ${Thread.currentThread().name}")
        return 31
    }
}

@Repository
class SampleRepository {
    suspend fun findSample(): SampleEntity {
        println("findSample() current thread name = ${Thread.currentThread().name}")
        return SampleEntity("levi", 29)
    }
}

data class SampleEntity(val name: String, val age: Int)