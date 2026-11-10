package app.rshmap.data

import android.content.Context
import app.rshmap.core.Profile
import app.rshmap.core.ProfileCodec
import app.rshmap.util.Dbg
import java.io.File

/** One JSON file per game package in filesDir/profiles. */
class ProfileStore(context: Context) {
    private val dir = File(context.applicationContext.filesDir, "profiles").apply { mkdirs() }

    private fun file(pkg: String) = File(dir, pkg.replace(Regex("[^A-Za-z0-9._-]"), "_") + ".json")

    fun get(pkg: String): Profile? {
        if (pkg.isEmpty()) return null
        val f = file(pkg)
        if (!f.exists()) return null
        return try {
            ProfileCodec.decode(f.readText())
        } catch (e: Exception) {
            Dbg.e("bad profile $pkg", e)
            null
        }
    }

    fun has(pkg: String) = pkg.isNotEmpty() && file(pkg).exists()

    fun save(p: Profile) {
        require(p.packageName.isNotEmpty()) { "profile needs a package name" }
        val tmp = File(dir, "tmp.json")
        tmp.writeText(ProfileCodec.encode(p))
        if (!tmp.renameTo(file(p.packageName))) {
            file(p.packageName).writeText(tmp.readText())
            tmp.delete()
        }
    }

    fun delete(pkg: String) {
        file(pkg).delete()
    }

    fun packages(): Set<String> {
        val out = HashSet<String>()
        for (f in dir.listFiles() ?: return out) {
            if (!f.name.endsWith(".json") || f.name == "tmp.json") continue
            try {
                val p = ProfileCodec.decode(f.readText())
                if (p.packageName.isNotEmpty()) out.add(p.packageName)
            } catch (ignored: Exception) {
            }
        }
        return out
    }
}
