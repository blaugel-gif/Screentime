package de.screentime.app.data

import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.HostKey
import com.jcraft.jsch.HostKeyRepository
import com.jcraft.jsch.JSch
import com.jcraft.jsch.JSchException
import com.jcraft.jsch.UIKeyboardInteractive
import com.jcraft.jsch.UserInfo
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.Base64

class ApiException(message: String) : Exception(message)

data class ExecResult(val exit: Int, val out: String, val err: String, val hostKey: String?)

/**
 * Merkt sich beim ersten Verbinden den Schlüssel des PCs ("trust on first use")
 * und verweigert später jede Verbindung zu einem Gerät mit anderem Schlüssel.
 */
private class TofuRepo(private val expected: String?) : HostKeyRepository {
    var seen: String? = null
    var mismatch = false

    override fun check(host: String?, key: ByteArray?): Int {
        if (key == null) return HostKeyRepository.NOT_INCLUDED
        val fp = Ssh.fingerprint(key)
        seen = fp
        return when {
            expected.isNullOrEmpty() -> HostKeyRepository.OK
            expected == fp -> HostKeyRepository.OK
            else -> {
                mismatch = true
                HostKeyRepository.CHANGED
            }
        }
    }

    override fun add(hostkey: HostKey?, ui: UserInfo?) {}
    override fun remove(host: String?, type: String?) {}
    override fun remove(host: String?, type: String?, key: ByteArray?) {}
    override fun getKnownHostsRepositoryID(): String = "screentime"
    override fun getHostKey(): Array<HostKey> = emptyArray()
    override fun getHostKey(host: String?, type: String?): Array<HostKey> = emptyArray()
}

private class PasswordInfo(private val pw: String) : UserInfo, UIKeyboardInteractive {
    override fun getPassphrase(): String? = null
    override fun getPassword(): String = pw
    override fun promptPassword(message: String?) = true
    override fun promptPassphrase(message: String?) = false
    override fun promptYesNo(message: String?) = false
    override fun showMessage(message: String?) {}
    override fun promptKeyboardInteractive(
        destination: String?, name: String?, instruction: String?,
        prompt: Array<out String>?, echo: BooleanArray?,
    ): Array<String> = Array(prompt?.size ?: 0) { pw }
}

object Ssh {
    fun fingerprint(key: ByteArray): String =
        "SHA256:" + Base64.getEncoder().withoutPadding()
            .encodeToString(MessageDigest.getInstance("SHA-256").digest(key))

    fun exec(c: Conn, command: String, timeoutMs: Int = 20_000): ExecResult {
        val jsch = JSch()
        val repo = TofuRepo(c.hostKey.ifBlank { null })
        jsch.hostKeyRepository = repo

        val session = jsch.getSession(c.user, c.host, c.port)
        session.setPassword(c.password)
        session.userInfo = PasswordInfo(c.password)
        session.setConfig("StrictHostKeyChecking", "yes")
        session.setConfig("PreferredAuthentications", "password,keyboard-interactive")

        try {
            session.connect(timeoutMs)
        } catch (e: JSchException) {
            throw friendly(e, repo.mismatch)
        }

        try {
            val ch = session.openChannel("exec") as ChannelExec
            ch.setCommand(command)
            val err = ByteArrayOutputStream()
            ch.setErrStream(err)
            val input = ch.inputStream
            ch.connect(timeoutMs)
            val out = input.readBytes()
            val start = System.currentTimeMillis()
            while (!ch.isClosed && System.currentTimeMillis() - start < timeoutMs) Thread.sleep(50)
            val code = ch.exitStatus
            ch.disconnect()
            return ExecResult(code, out.toString(Charsets.UTF_8), err.toString("UTF-8"), repo.seen)
        } catch (e: JSchException) {
            throw friendly(e, false)
        } finally {
            session.disconnect()
        }
    }

    /**
     * Öffnet einen dauerhaften Kanal (für den Sprachanruf). Der Aufrufer liest
     * [SshStream.input], schreibt [SshStream.output] und ruft am Ende [SshStream.close].
     */
    fun stream(c: Conn, command: String, timeoutMs: Int = 20_000): SshStream {
        val jsch = JSch()
        val repo = TofuRepo(c.hostKey.ifBlank { null })
        jsch.hostKeyRepository = repo
        val session = jsch.getSession(c.user, c.host, c.port)
        session.setPassword(c.password)
        session.userInfo = PasswordInfo(c.password)
        session.setConfig("StrictHostKeyChecking", "yes")
        session.setConfig("PreferredAuthentications", "password,keyboard-interactive")
        try {
            session.connect(timeoutMs)
            session.setServerAliveInterval(10_000)
            val ch = session.openChannel("exec") as ChannelExec
            ch.setCommand(command)
            ch.setErrStream(ByteArrayOutputStream())
            val input = ch.inputStream
            val output = ch.outputStream
            ch.connect(timeoutMs)
            return SshStream(session, ch, input, output, repo.seen)
        } catch (e: JSchException) {
            session.disconnect()
            throw friendly(e, repo.mismatch)
        }
    }

    private fun friendly(e: JSchException, mismatch: Boolean): Exception {
        val msg = e.message ?: ""
        return when {
            mismatch -> ApiException(
                "Der PC weist sich anders aus als bisher. Falls er neu installiert wurde: " +
                    "In den Einstellungen „PC-Schlüssel zurücksetzen“ tippen."
            )
            msg.contains("Auth fail", true) || msg.contains("Auth cancel", true) ->
                ApiException("Benutzername oder Passwort falsch.")
            msg.contains("timeout", true) || msg.contains("UnknownHost", true) ||
                msg.contains("connect", true) || msg.contains("route", true) ->
                ApiException("PC nicht erreichbar. Ist er an und bist du im selben WLAN (oder per VPN verbunden)?")
            else -> ApiException("Verbindungsfehler: $msg")
        }
    }
}

class SshStream(
    private val session: com.jcraft.jsch.Session,
    private val channel: ChannelExec,
    val input: java.io.InputStream,
    val output: java.io.OutputStream,
    val hostKey: String?,
) {
    fun close() {
        runCatching { output.close() }
        runCatching { channel.disconnect() }
        runCatching { session.disconnect() }
    }
}
