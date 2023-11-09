package no.telenor.kt

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.MDC

/**
 * The [MDCTransaction] class can be used to make modifications to the current [MDC]'s context,
 * and keep track of the current changes. The changes can easily be undone by calling [MDCTransaction.restore].
 *
 * Example:
 *
 * ```kt
 * MDC.put("foo", "bar")
 * MDC.put("qux", "quux")
 *
 * val mySnapshot = MDCTransaction
 *   .put("foo", "baz")
 *   .put("hello", "world")
 *   .remove("qux")
 *   .commit()
 *
 * assertEquals("baz", MDC.get("foo"))
 * assertEquals("world", MDC.get("hello"))
 * assertEquals(null, MDC.get("qux"))
 *
 * mySnapshot.restore()
 *
 * assertEquals("bar", MDC.get("foo"))
 * assertEquals(null, MDC.get("hello"))
 * assertEquals("quux", MDC.get("qux"))
 * ```
 */
class MDCTransaction private constructor(private var snapshot: Map<String, MdcValueDiff>?) {
	companion object {
		private val log: Logger = LoggerFactory.getLogger(MDCTransaction::class.java)
		private val mdcCopy: Map<String, String?>
			get() {
				var mdc: Map<String, String?>? = MDC.getCopyOfContextMap()
				if (mdc == null) {
					mdc = mutableMapOf()
					MDC.setContextMap(mdc)
				}
				return mdc
			}

		private fun mdcNow(mdc: Map<String, String?>, key: String) =
			if (mdc.containsKey(key)) MdcValue.some(mdc[key]) else MdcValue.none

		fun builder() = Builder()
		fun put(key: String, newValue: String?) = builder().put(key, newValue)
		fun put(key: String, newValue: String?, defaultValue: String) = builder().put(key, newValue, defaultValue)
		fun putIfNotNull(key: String, newValue: String?) = builder().putIfNotNull(key, newValue)
		fun remove(key: String) = builder().remove(key)
		fun clear() = builder().clear()
	}

	/**
	 * Undo all the changes made to the [MDC] using this transaction.
	 *
	 * Note that if any value stored in this snapshot differs from the
	 * value currently in the [MDC], it will not be undone.
	 *
	 * Example:
	 *
	 * ```kt
	 * MDC.put("foo", "bar")
	 * assertEquals("bar", MDC.get("foo"))
	 *
	 * val mySnapshot = MDCTransaction.put("foo", "baz").commit()
	 * assertEquals("baz", MDC.get("foo"))
	 *
	 * MDC.put("foo", "qux")
	 * assertEquals("qux", MDC.get("foo"))
	 *
	 * mySnapshot.restore()
	 * assertEquals("bar", MDC.get("foo"))
	 * // Assertion error here, expected value "bar", got value "qux"
	 * ```
	 */
	fun restore() {
		val mdc = mdcCopy
		val snapshot = snapshot ?: throw RuntimeException("MDCTransaction already restored!")
		this.snapshot = null
		for ((key, diff) in snapshot) {
			val now = mdcNow(mdc, key)

			if (diff.expected.exists != now.exists || diff.expected.value != now.value) {
				// We don't want to restore the original MDC value
				// when the current MDC value is not equal to the
				// MDC value that was set in this snapshot. That
				// means that another part of the application took
				// over the MDC key, and thus that part of application
				// should clean up after themselves. This is a feature
				// and not a bug. We don't want to accidentally break
				// the application if the application expects the
				// current value to remain the same after this snapshot
				// is restored.
				log.debug("Not restoring '$key' (expected(${diff.expected}) now($now), value from MDC differs from expected value in snapshot")
				continue
			}

			if (diff.original.exists) MDC.put(key, diff.original.value)
			else MDC.remove(key)
		}
	}

	class Builder internal constructor(internal var changes: MutableMap<String, MdcValue>? = mutableMapOf()) {
		private fun assertNotCommitted() = changes ?: throw RuntimeException("Cannot modify committed MDCSnapshot.Builder!")

		fun put(key: String, value: String?): Builder {
			assertNotCommitted()[key] = MdcValue.some(value)
			return this
		}

		fun put(key: String, value: String?, defaultValue: String): Builder = put(key, value ?: defaultValue)
		fun putIfNotNull(key: String, value: String?) = value?.let { put(key, value) } ?: this

		fun remove(key: String): Builder {
			assertNotCommitted()[key] = MdcValue.none
			return this
		}

		fun clear(): Builder {
			val changes = assertNotCommitted()
			for (key in mdcCopy.keys) changes[key] = MdcValue.none
			return this
		}

		fun commit(): MDCTransaction {
			val changes = assertNotCommitted()
			this.changes = null
			val committed = mutableMapOf<String, MdcValueDiff>()
			val mdc = mdcCopy
			for ((key, expected) in changes) {
				committed[key] = MdcValueDiff(
					mdcNow(mdc, key),
					expected,
				)
				if (expected.exists) MDC.put(key, expected.value)
				else MDC.remove(key)
				// Note about null values.
				// Ref. https://thedoozers.slack.com/archives/C061NRQ78US/p1699440880341159?thread_ts=1699388748.049189&cid=C061NRQ78US
				// Some MDC adapters MIGHT NOT support `null` values. However,
				// the default MDC adapter in logback does support `null` values.
				// If the implementors of an application absolutely insists on
				// swapping out the default logback MDC adapter for whatever
				// reason, they can also do null checks on their own.
			}
			return MDCTransaction(committed)
		}
	}

	internal data class MdcValueDiff(val original: MdcValue, val expected: MdcValue)

	internal data class MdcValue(val exists: Boolean, val value: String?) {
		companion object {
			fun some(value: String?) = MdcValue(true, value)
			val none = MdcValue(false, null)
		}
	}
}
