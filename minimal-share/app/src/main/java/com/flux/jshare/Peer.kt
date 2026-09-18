package com.flux.jshare

data class Peer(
    val id: String,
    val name: String,
    val host: String,
    val port: Int,
    val lastSeen: Long = System.currentTimeMillis()
)
