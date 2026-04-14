package com.kail.location.utils

import java.io.File

object ShellUtils {
    fun hasRoot(): Boolean {
        val runtime = Runtime.getRuntime()
        try {
            val process = runtime.exec("su")
            process.outputStream.write("exit\n".toByteArray())
            process.outputStream.flush()
            process.waitFor()
            return process.exitValue() == 0
        } catch (e: Exception) {
            return false
        }
    }

    fun executeCommand(command: String): String {
        KailLog.i(null, "ShellUtils", ">>> executeCommand: $command")
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))

            val result = process.inputStream.bufferedReader().readText()
            val error = process.errorStream.bufferedReader().readText()

            process.waitFor()

            if (process.exitValue() != 0) {
                KailLog.e(null, "ShellUtils", ">>> command failed: $command, error: $error")
                error
            } else {
                KailLog.i(null, "ShellUtils", ">>> command succeeded: $command, result: $result")
                result
            }
        } catch (e: Exception) {
            KailLog.e(null, "ShellUtils", ">>> command exception: $command, error: ${e.message}")
            e.printStackTrace()
            ""
        }
    }


    fun executeCommandToBytes(command: String): ByteArray {
        val runtime = Runtime.getRuntime()
        try {
            val process = runtime.exec("su")
            process.outputStream.write("$command\n".toByteArray())
            process.outputStream.write("exit\n".toByteArray())
            process.outputStream.flush()
            process.waitFor()
            if (process.exitValue() != 0) {
                return process.errorStream.use { it.readBytes() }
            }
            return process.inputStream.use { it.readBytes() }
        } catch (e: Exception) {
            e.printStackTrace()
            return ByteArray(0)
        }
    }
}
