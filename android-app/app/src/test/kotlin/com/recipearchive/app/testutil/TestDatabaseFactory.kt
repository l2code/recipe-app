package com.recipearchive.app.testutil

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.recipearchive.app.data.local.RecipeDatabase
import java.util.concurrent.Executor

/** Runs a [Runnable] synchronously on the calling thread. */
private object SynchronousExecutor : Executor {
    override fun execute(command: Runnable) = command.run()
}

object TestDatabaseFactory {
    /**
     * Room normally runs suspend queries on its own real background thread pool. In coroutine
     * tests driven by a [kotlinx.coroutines.test.TestDispatcher], that races with
     * `advanceUntilIdle()`, which only knows about virtual-time work, not real concurrent
     * threads. Forcing a synchronous executor makes every query run on the calling thread so
     * ViewModel tests can deterministically await results.
     */
    fun create(): RecipeDatabase {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        return Room.inMemoryDatabaseBuilder(context, RecipeDatabase::class.java)
            .setQueryExecutor(SynchronousExecutor)
            .setTransactionExecutor(SynchronousExecutor)
            .allowMainThreadQueries()
            .build()
    }
}
