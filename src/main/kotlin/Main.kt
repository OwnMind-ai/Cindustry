package org.cindustry

import org.cindustry.exceptions.TokenException
import org.cindustry.transpiler.Transpiler
import java.io.File

fun main(args: Array<String>) {
    if (args.isEmpty() || args.size == 1 && args[0] in listOf("-h", "--help")){
        println("Usage: cind [-o <output file>] [-d <module directory>] <main file>")
        return
    }

    val arguments: MutableMap<String?, String?> = hashMapOf(
        Pair("-d", System.getProperty("user.dir")),     // Module directory
        Pair("-o", null),                               // Output file (System.out if null)
        Pair(null, null)                                // Main file
    )

    parseArgs(args, arguments)?.let{
        System.err.println("$it\n\nUse --help for more info")
        return
    }

    val directory = File(arguments["-d"]!!)
    if (!directory.exists() || !directory.isDirectory){
        System.err.println("Directory '${arguments["-d"]}' doesn't exists\n\nUse --help for more info")
        return
    }

    val output = arguments["-o"]?.let { File(it) }

    if (arguments[null] == null) {
        System.err.println("No main file was specified\n\nUse --help for more info")
        return
    }

    val mainFile = File(arguments[null]!!)
    if (!mainFile.exists() || !mainFile.isFile){
        System.err.println("Main file '${arguments[null]}' doesn't exists\n\nUse --help for more info")
        return
    }

    try {
        val mainTree = Transpiler.getParsed(mainFile)
        val result = Transpiler(mainTree, directory).transpile()

        if (output != null){
            output.createNewFile()
            output.writeText(result)
            println("Compilation is done! Check the '${output.path}' for the result")
        } else {
            println(result)
        }
    } catch (e: TokenException){
        System.err.println(e)
        return
    }
}

fun parseArgs(args: Array<String>, result: MutableMap<String?, String?>): String? {
    var i = 0
    while (i < args.size) {
        val flag = args[i++]
        if (!flag.startsWith("-")){
            if (result[null] != null)
                return "Undefined argument $flag"

            result[null] = flag
        } else if (flag == "-o"){
            result["-o"] = args[i++]
        }  else if (flag == "-d"){
            result["-d"] = args[i++]
        } else
            return "Undefined argument $flag"
    }

    return null
}
