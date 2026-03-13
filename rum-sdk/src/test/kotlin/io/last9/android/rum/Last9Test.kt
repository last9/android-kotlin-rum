package io.last9.android.rum

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class Last9Test {

    private lateinit var app: Application

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        Last9.reset()
    }

    @After
    fun tearDown() {
        Last9.reset()
    }

    // -------------------------------------------------------------------------
    // P0 — getInstance contract (2.2.1)
    // -------------------------------------------------------------------------

    @Test
    fun `getInstance throws before init`() {
        val ex = assertThrows(IllegalStateException::class.java) {
            Last9.getInstance()
        }
        assertTrue("Expected error message about SDK not initialized", ex.message!!.contains("Last9 SDK has not been initialized"))
    }

    // -------------------------------------------------------------------------
    // P0 — Thread safety (2.2.4)
    // -------------------------------------------------------------------------

    @Test
    fun `concurrent init calls produce exactly one instance`() {
        val threadCount = 20
        val latch = CountDownLatch(1)
        val results = ArrayList<Last9RumInstance>(threadCount)
        val executor = Executors.newFixedThreadPool(threadCount)

        repeat(threadCount) {
            executor.submit {
                latch.await()
                val instance = Last9.init(app) {
                    token = "tok"
                    serviceName = "svc"
                }
                synchronized(results) { results.add(instance) }
            }
        }

        latch.countDown()
        executor.shutdown()
        executor.awaitTermination(10, TimeUnit.SECONDS)

        assertEquals(threadCount, results.size)
        val first = results[0]
        results.forEach { instance ->
            assertSame("All concurrent inits must return the same instance", first, instance)
        }
    }

    // -------------------------------------------------------------------------
    // P1 — Init lifecycle (2.2.2, 2.2.3, 2.2.5, 2.2.6)
    // -------------------------------------------------------------------------

    @Test
    fun `init returns same instance on second call`() {
        val first = Last9.init(app) { token = "tok"; serviceName = "svc" }
        val second = Last9.init(app) { token = "tok2"; serviceName = "svc2" }

        assertSame(first, second)
    }

    @Test
    fun `getInstance returns the initialized instance`() {
        val init = Last9.init(app) { token = "tok"; serviceName = "svc" }
        val get = Last9.getInstance()

        assertSame(init, get)
        assertNotNull(get.otelRum)
    }

    @Test
    fun `createOkHttpInterceptor throws when enableOkHttpInstrumentation is false`() {
        val instance = Last9.init(app) {
            token = "tok"
            serviceName = "svc"
            enableOkHttpInstrumentation = false
        }

        assertThrows(IllegalStateException::class.java) {
            instance.createOkHttpInterceptor()
        }
    }

    @Test
    fun `init throws when token is blank`() {
        assertThrows(IllegalArgumentException::class.java) {
            Last9.init(app) { serviceName = "svc" } // token left blank
        }
    }

    @Test
    fun `init throws when serviceName is blank`() {
        assertThrows(IllegalArgumentException::class.java) {
            Last9.init(app) { token = "tok" } // serviceName left blank
        }
    }

    @Test
    fun `failed init leaves singleton unset so a subsequent correct call succeeds`() {
        try {
            Last9.init(app) { /* no token, no serviceName */ }
        } catch (_: IllegalArgumentException) { }

        // After a failed init, a valid init must succeed
        val instance = Last9.init(app) { token = "tok"; serviceName = "svc" }
        assertNotNull(instance)
        assertSame(instance, Last9.getInstance())
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun assertTrue(message: String, condition: Boolean) =
        org.junit.Assert.assertTrue(message, condition)

    private fun assertEquals(expected: Int, actual: Int) =
        org.junit.Assert.assertEquals(expected.toLong(), actual.toLong())
}
