package com.thewizrd.simplewear.helpers

import android.os.Build
import androidx.annotation.RequiresApi
import java.util.LinkedList
import java.util.function.Predicate

enum class ListChangedAction {
    ADD,
    MOVE,
    REMOVE,
    REPLACE,
    RESET
}

class ListChangedArgs<T> {
    val action: ListChangedAction
    val newStartingIndex: Int
    val oldStartingIndex: Int
    val oldItems: List<T>?
    val newItems: List<T>?

    constructor(action: ListChangedAction, newStartingIndex: Int, oldStartingIndex: Int) {
        this.action = action
        this.newStartingIndex = newStartingIndex
        this.oldStartingIndex = oldStartingIndex
        this.oldItems = null
        this.newItems = null
    }

    constructor(
        action: ListChangedAction,
        newStartingIndex: Int,
        oldStartingIndex: Int,
        oldItems: List<T>?,
        newItems: List<T>?
    ) {
        this.action = action
        this.newStartingIndex = newStartingIndex
        this.oldStartingIndex = oldStartingIndex
        this.oldItems = oldItems
        this.newItems = newItems
    }
}


interface OnListChangedListener<T> {
    /**
     * Called whenever a change of unknown type has occurred, such as the entire list being
     * set to new values.
     *
     * @param sender The changing list.
     * @param args   The data for the onChanged event.
     */
    fun onChanged(sender: ArrayList<T>, args: ListChangedArgs<T>)
}

class CallbackList<T> {
    private val mCallbacks: MutableList<OnListChangedListener<T>> = mutableListOf()

    fun add(callback: OnListChangedListener<T>) {
        mCallbacks.add(callback)
    }

    fun remove(callback: OnListChangedListener<T>) {
        mCallbacks.remove(callback)
    }

    fun notifyChange(sender: ArrayList<T>, args: ListChangedArgs<T>) {
        for (i in mCallbacks.indices) {
            mCallbacks[i].onChanged(sender, args)
        }
    }
}

open class ObservableArrayList<T> : ArrayList<T> {
    @delegate:Transient
    protected val mListeners: CallbackList<T> by lazy { CallbackList() }

    constructor() : super()

    constructor(initialCapacity: Int) : super(initialCapacity)

    constructor(c: MutableCollection<out T>) : super(c)

    fun addOnListChangedCallback(listChangedListener: OnListChangedListener<T>) {
        mListeners.add(listChangedListener)
    }

    fun removeOnListChangedCallback(listChangedListener: OnListChangedListener<T>) {
        mListeners.remove(listChangedListener)
    }

    fun move(oldIndex: Int, newIndex: Int) {
        super.set(oldIndex, super.set(newIndex, super.get(oldIndex)))

        mListeners.notifyChange(
            this,
            ListChangedArgs(ListChangedAction.MOVE, newIndex, oldIndex)
        )
    }

    override fun set(index: Int, element: T): T {
        val oldVal = super.set(index, element)
        mListeners.notifyChange(
            this,
            ListChangedArgs(
                ListChangedAction.REPLACE,
                index,
                index,
                listOf(oldVal),
                listOf(element)
            )
        )
        return oldVal
    }

    override fun add(t: T): Boolean {
        super.add(t)
        mListeners.notifyChange(
            this,
            ListChangedArgs(ListChangedAction.ADD, size - 1, -1, null, listOf(t))
        )
        return true
    }

    override fun add(index: Int, element: T) {
        super.add(index, element)
        mListeners.notifyChange(
            this,
            ListChangedArgs(ListChangedAction.ADD, index, -1, null, listOf(element))
        )
    }

    override fun removeAt(index: Int): T {
        val `val` = super.removeAt(index)
        mListeners.notifyChange(
            this,
            ListChangedArgs(ListChangedAction.REMOVE, -1, index, listOf(`val`), null)
        )
        return `val`
    }

    override fun remove(o: T): Boolean {
        val index = indexOf(o)
        if (index >= 0) {
            removeAt(index)
            return true
        } else {
            return false
        }
    }

    override fun clear() {
        val oldSize = size
        super.clear()
        if (oldSize != 0) {
            mListeners.notifyChange(
                this,
                ListChangedArgs(ListChangedAction.RESET, -1, -1)
            )
        }
    }

    override fun addAll(c: Collection<T>): Boolean {
        val oldSize = size
        val added = super.addAll(c)
        if (added) {
            mListeners.notifyChange(
                this,
                ListChangedArgs(ListChangedAction.ADD, oldSize - 1, -1, null, LinkedList<T>(c))
            )
        }
        return added
    }

    override fun addAll(index: Int, c: Collection<T>): Boolean {
        val added = super.addAll(index, c)
        if (added) {
            mListeners.notifyChange(
                this,
                ListChangedArgs(ListChangedAction.ADD, index, -1, null, LinkedList<T>(c))
            )
        }
        return added
    }

    override fun removeRange(fromIndex: Int, toIndex: Int) {
        val oldItems = this.subList(fromIndex, toIndex)
        super.removeRange(fromIndex, toIndex)
        mListeners.notifyChange(
            this,
            ListChangedArgs(ListChangedAction.REMOVE, fromIndex, -1, oldItems, null)
        )
    }

    override fun removeAll(c: Collection<T>): Boolean {
        val value = super.removeAll(c)
        mListeners.notifyChange(
            this,
            ListChangedArgs(
                ListChangedAction.REMOVE,
                -1,
                -1,
                LinkedList(c),
                null
            )
        )
        return value
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    override fun removeIf(filter: Predicate<in T>): Boolean {
        val value = super.removeIf(filter)
        mListeners.notifyChange(
            this,
            ListChangedArgs(ListChangedAction.REMOVE, -1, -1)
        )
        return value
    }
}