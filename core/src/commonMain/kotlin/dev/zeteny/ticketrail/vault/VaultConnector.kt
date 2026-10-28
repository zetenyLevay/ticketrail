package dev.zeteny.ticketrail.vault

import dev.zeteny.ticketrail.config.ServiceConfigStore
import okio.FileSystem

val rootPath = "/mnt/everything/Nexus/"
val profilePath = "/mnt/everything/Nexus/90_Templates/_Ticket_Profile"

class VaultConnector (private val fs: FileSystem, private val store: ServiceConfigStore){

}
