package com.g2s.trading

interface LockManager {
    fun acquire()

    fun release()
}