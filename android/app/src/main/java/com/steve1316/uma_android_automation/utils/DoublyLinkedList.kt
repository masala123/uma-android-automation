package com.steve1316.uma_android_automation.utils

open class DoublyLinkedListNode<T>(
    val list: DoublyLinkedList<T>,
    var value: T,
    var next: DoublyLinkedListNode<T>? = null,
    var prev: DoublyLinkedListNode<T>? = null,
)

open class DoublyLinkedList<T> {
    private var head: DoublyLinkedListNode<T>? = null
    private var tail: DoublyLinkedListNode<T>? = null
    var size: Int = 0
        private set
    
    fun isEmpty(): Boolean {
        return size == 0
    }

    fun push(value: T): DoublyLinkedListNode<T> {
        val newNode: DoublyLinkedListNode<T> = DoublyLinkedListNode(this, value)
        if (isEmpty()) {
            head = newNode
            tail = newNode
        } else {
            newNode.next = head
            head?.prev = newNode
            head = newNode
        }
        size++
        return newNode
    }

    fun pop(): T? {
        if (isEmpty()) {
            return null
        }
        val result: T? = head?.value
        head = head?.next
        head?.prev = null
        size--
        if (isEmpty()) {
            tail = null
        }
        return result
    }

    fun append(value: T): DoublyLinkedListNode<T> {
        val newNode: DoublyLinkedListNode<T> = DoublyLinkedListNode(this, value)
        if (isEmpty()) {
            head = newNode
            tail = newNode
        } else {
            newNode.prev = tail
            tail?.next = newNode
            tail = newNode
        }
        size++
        return newNode
    }

    fun find(value: T): DoublyLinkedListNode<T>? {
        var curr = head
        while (curr != null) {
            if (curr.value == value) {
                return curr
            }
            curr = curr.next
        }
        return null
    }

    fun findIndex(value: T): Int? {
        var curr = head
        var i: Int = 0
        while (curr != null) {
            if (curr.value == value) {
                return i
            }
            curr = curr.next
            i++
        }
        return null
    }

    fun getValues(): List<T> {
        val res: MutableList<T> = mutableListOf()
        var curr = head
        while (curr != null) {
            res.add(curr.value)
            curr = curr.next
        }
        return res.toList()
    }

    override fun toString(): String {
        if (isEmpty()) {
            return "Empty List"
        }
        val sb = StringBuilder()
        var current = head
        while (current != null) {
            sb.append(current.value).append(if (current.next != null) " <-> " else "")
            current = current.next
        }
        return sb.toString()
    }
}
