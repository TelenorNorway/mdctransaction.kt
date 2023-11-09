package no.telenor.kt

import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import org.slf4j.MDC
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class MDCTransactionTest {
	// region Test cleanup
	private var mdc: Map<String, String?>? = null

	@BeforeTest
	fun prepare() {
		mdc = MDC.getCopyOfContextMap()
		MDC.clear()
	}

	@AfterTest
	fun cleanup() {
		MDC.setContextMap(mdc)
		mdc = null
	}
	// endregion

	@Test
	fun `Non existing MDC value is removed after restore`() {
		val snapshot = MDCTransaction.put("foo", "bar").commit()
		assertEquals("bar", MDC.get("foo"))
		snapshot.restore()
		assertEquals(false, MDC.getCopyOfContextMap().containsKey("foo"))
	}

	@Test
	fun `Existing MDC key is restored to original value after being modified`() {
		MDC.put("foo", "bar")
		val snapshot = MDCTransaction.put("foo", "baz").commit()
		assertEquals("baz", MDC.get("foo"))
		snapshot.restore()
		assertEquals("bar", MDC.get("foo"))
	}

	@Test
	fun `Existing MDC keys is restored to original value after being cleared`() {
		MDC.put("foo", "bar")
		MDC.put("baz", "qux")
		val snapshot = MDCTransaction.clear().commit()
		assertEquals(true, MDC.getCopyOfContextMap().isEmpty())
		snapshot.restore()
		assertAll(
			{ assertEquals("bar", MDC.get("foo")) },
			{ assertEquals("qux", MDC.get("baz")) },
		)
	}

	@Test
	fun `Existing MDC key is restored to original value after being removed`() {
		MDC.put("foo", "bar")
		val snapshot = MDCTransaction.remove("foo").commit()
		assertEquals(true, MDC.getCopyOfContextMap().isEmpty())
		snapshot.restore()
		assertEquals("bar", MDC.get("foo"))
	}

	@Test
	fun `Non existing MDC key is not restored when added by snapshot, but manually modified later`() {
		val snapshot = MDCTransaction.put("foo", "bar").commit()
		assertEquals("bar", MDC.get("foo"))
		MDC.put("foo", "baz")
		assertEquals("baz", MDC.get("foo"))
		snapshot.restore()
		assertEquals("baz", MDC.get("foo"))
	}

	@Test
	fun `Existing MDC key is not restored when modified by snapshot, but manually modified later`() {
		MDC.put("foo", "bar")
		val snapshot = MDCTransaction.put("foo", "bar").commit()
		assertEquals("bar", MDC.get("foo"))
		MDC.put("foo", "qux")
		assertEquals("qux", MDC.get("foo"))
		snapshot.restore()
		assertEquals("qux", MDC.get("foo"))
	}

	@Test
	fun `Default values in put`() {
		MDCTransaction.put("foo", "bar", "baz").commit()
		assertEquals("bar", MDC.get("foo"))
		MDCTransaction.put("foo", null, "baz").commit()
		assertEquals("baz", MDC.get("foo"))
	}

	@Test
	fun `No mutations after commit`() {
		val builder = MDCTransaction.clear()
		builder.commit()
		assertAll(
			{ assertThrows<RuntimeException> { builder.put("a", "b") } },
			{ assertThrows<RuntimeException> { builder.remove("a") } },
			{ assertThrows<RuntimeException> { builder.clear() } },
		)
	}

	@Test
	fun `Cannot restore twice`() {
		val snapshot = MDCTransaction.clear().commit()
		snapshot.restore()
		assertThrows<RuntimeException> { snapshot.restore() }
	}

	@Test
	fun `put if not null`() {
		MDCTransaction.putIfNotNull("foo", null).commit()
		assertEquals(true, MDC.getCopyOfContextMap().isEmpty())
		MDCTransaction.putIfNotNull("foo", "bar").commit()
		assertEquals("bar", MDC.get("foo"))
	}
}
