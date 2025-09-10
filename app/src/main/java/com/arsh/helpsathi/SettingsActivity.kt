package com.arsh.helpsathi

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class SettingsActivity : AppCompatActivity() {

    private lateinit var contacts: MutableList<Contact>
    private lateinit var contactsAdapter: ContactsAdapter

    private lateinit var customMessageEditText: EditText
    private lateinit var contactNameEditText: EditText
    private lateinit var contactNumberEditText: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        contacts = StorageManager.loadContacts(this)
        val customMessage = StorageManager.loadCustomMessage(this)


        customMessageEditText = findViewById(R.id.customMessageEditText)
        contactNameEditText = findViewById(R.id.contactNameEditText)
        contactNumberEditText = findViewById(R.id.contactNumberEditText)
        val saveMessageButton: Button = findViewById(R.id.saveMessageButton)
        val addContactButton: Button = findViewById(R.id.addContactButton)
        val contactsRecyclerView: RecyclerView = findViewById(R.id.contactsRecyclerView)


        customMessageEditText.setText(customMessage)
        saveMessageButton.setOnClickListener {
            StorageManager.saveCustomMessage(this, customMessageEditText.text.toString())
            Toast.makeText(this, "Message saved!", Toast.LENGTH_SHORT).show()
        }


        contactsAdapter = ContactsAdapter(contacts) { contactToDelete ->

            contacts.remove(contactToDelete)
            StorageManager.saveContacts(this, contacts)
            contactsAdapter.notifyDataSetChanged()
            Toast.makeText(this, "Contact deleted", Toast.LENGTH_SHORT).show()
        }
        contactsRecyclerView.adapter = contactsAdapter
        contactsRecyclerView.layoutManager = LinearLayoutManager(this)


        addContactButton.setOnClickListener {
            addContact()
        }
    }

    private fun addContact() {
        val name = contactNameEditText.text.toString().trim()
        val number = contactNumberEditText.text.toString().trim()

        if (name.isNotEmpty() && number.isNotEmpty()) {
            val newContact = Contact(name, number)
            contacts.add(newContact)
            StorageManager.saveContacts(this, contacts)
            contactsAdapter.notifyItemInserted(contacts.size - 1)

            contactNameEditText.text.clear()
            contactNumberEditText.text.clear()
        } else {
            Toast.makeText(this, "Name and number cannot be empty", Toast.LENGTH_SHORT).show()
        }
    }
}