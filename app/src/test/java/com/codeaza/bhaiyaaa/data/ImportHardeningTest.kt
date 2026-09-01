package com.codeaza.bhaiyaaa.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.codeaza.bhaiyaaa.data.db.AppDatabase
import com.codeaza.bhaiyaaa.data.export.DataTransfer
import com.codeaza.bhaiyaaa.data.export.TransferResult
import com.codeaza.bhaiyaaa.domain.model.VipLevel
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * What an import is allowed to do to the database.
 *
 * Import is the one place the app reads a file it did not write. It is not a
 * hostile-input surface in the usual sense - the user picks the file - but "the
 * user picked the wrong file" and "the file is truncated" are ordinary, and the
 * app should answer both with a message rather than a crash or a half-restored
 * database.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ImportHardeningTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var db: AppDatabase
    private lateinit var transfer: DataTransfer

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        AppDatabase.setInstanceForTest(db)
        transfer = DataTransfer(context)
    }

    @After
    fun tearDown() {
        AppDatabase.setInstanceForTest(null)
        db.close()
    }

    /** Writes [text] to a file and hands back a URI the importer can open. */
    private fun fileUri(text: String): android.net.Uri {
        val file = File(context.cacheDir, "import-test.json")
        file.writeText(text)
        return android.net.Uri.fromFile(file)
    }

    /** Comfortably past the importer's 32 MB ceiling. */
    private val OVER_THE_CAP_MEGABYTES = 33

    private fun export(contacts: String) = """
        {
          "format": "${DataTransfer.FORMAT_NAME}",
          "version": 1,
          "contacts": [$contacts]
        }
    """.trimIndent()

    @Test
    fun `a file that is not JSON is refused with a message`() = runTest {
        val result = transfer.import(fileUri("this is not json"))

        assertThat(result).isInstanceOf(TransferResult.Failure::class.java)
    }

    @Test
    fun `a JSON file that is not an export is refused`() = runTest {
        val result = transfer.import(fileUri("""{"hello":"world"}"""))

        assertThat(result).isInstanceOf(TransferResult.Failure::class.java)
    }

    @Test
    fun `a file larger than the cap is refused rather than read into memory`() = runTest {
        // readBytes() used to pull the whole file in whatever its size, so a
        // mis-picked video was an out-of-memory kill rather than a message.
        // Streamed to disk rather than built in memory, so the test does not
        // reproduce the very problem it is checking for.
        val file = File(context.cacheDir, "huge-import.json")
        file.outputStream().buffered().use { out ->
            val chunk = ByteArray(1024 * 1024) { '0'.code.toByte() }
            repeat(OVER_THE_CAP_MEGABYTES) { out.write(chunk) }
        }

        val result = transfer.import(android.net.Uri.fromFile(file))
        file.delete()

        assertThat(result).isInstanceOf(TransferResult.Failure::class.java)
        assertThat((result as TransferResult.Failure).message).contains("too large")
    }

    @Test
    fun `an unknown VIP tier in a file cannot reach the database`() = runTest {
        // This column decides how loudly the phone rings for someone, so an
        // arbitrary string from a file has no business in it.
        val result = transfer.import(
            fileUri(
                export("""{"phoneNumber":"+923001234567","name":"Ali","vipLevel":"SUPREME_LEADER"}""")
            )
        )

        assertThat(result).isInstanceOf(TransferResult.Success::class.java)
        val stored = db.contactDao().allOnce().single()
        assertThat(VipLevel.entries.map { it.storageValue }).contains(stored.vipLevel)
        assertThat(stored.vipLevel).isEqualTo(VipLevel.NONE.storageValue)
    }

    @Test
    fun `a known VIP tier survives the round trip`() = runTest {
        transfer.import(
            fileUri(
                export("""{"phoneNumber":"+923001234567","name":"Ali","vipLevel":"EMERGENCY"}""")
            )
        )

        assertThat(db.contactDao().allOnce().single().vipLevel)
            .isEqualTo(VipLevel.EMERGENCY.storageValue)
    }

    @Test
    fun `an absurd importance is brought into range`() = runTest {
        transfer.import(
            fileUri(
                export("""{"phoneNumber":"+923001234567","name":"Ali","importance":9999}""")
            )
        )

        assertThat(db.contactDao().allOnce().single().importance).isAtMost(5)
    }

    @Test
    fun `a malformed entry is skipped rather than failing the whole import`() = runTest {
        val result = transfer.import(
            fileUri(
                export(
                    """{"name":"No number here"},""" +
                        """{"phoneNumber":"+923001234567","name":"Ali"}"""
                )
            )
        )

        assertThat(result).isInstanceOf(TransferResult.Success::class.java)
        // The good one landed; the one with no number was skipped.
        assertThat(db.contactDao().allOnce()).hasSize(1)
    }

    @Test
    fun `importing the same file twice does not duplicate anything`() = runTest {
        val uri = fileUri(
            export("""{"phoneNumber":"+923001234567","name":"Ali","vipLevel":"VIP"}""")
        )

        transfer.import(uri)
        transfer.import(uri)

        assertThat(db.contactDao().allOnce()).hasSize(1)
    }
}
