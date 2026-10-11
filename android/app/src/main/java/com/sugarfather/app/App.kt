package com.sugarfather.app

import android.app.Application

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Repo.init(this)
        Notify.ensureChannel(this)
        ReminderScheduler.rescheduleAll(this, Repo.reminders)
    }
}
