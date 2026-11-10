package com.example.viaapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel

val ViaTeal = Color(0xFF00A884)
val ViaDarkGreen = Color(0xFF008069)
val ViaChatBackground = Color(0xFFEFE7DE)
val ViaOutgoingBubble = Color(0xFFD9FDD3)

data class ViaContact(val name: String, val phoneNumber: String)
data class ViaMessage(val senderPhone: String, val text: String)

class ViaViewModel : ViewModel() {
    var screen by mutableStateOf("LOGIN")
    var inputPhone by mutableStateOf("")
    var inputPassword by mutableStateOf("")
    var generatedOtp by mutableStateOf("")
    var enteredOtp by mutableStateOf("")
    var myPhone by mutableStateOf("")

    val registeredUsers = mutableStateMapOf(
        "08012345678" to "Brother John",
        "09087654321" to "Musa Repairs"
    )

    val myContacts = mutableStateListOf<ViaContact>()
    val messages = mutableStateListOf<ViaMessage>()
    var activeContact by mutableStateOf<ViaContact?>(null)

    fun sendOtp() {
        if (inputPhone.length >= 10 && inputPassword.isNotBlank()) {
            generatedOtp = (100000..999999).random().toString()
            screen = "OTP"
        }
    }

    fun verify() {
        if (enteredOtp == generatedOtp) {
            myPhone = inputPhone.trim()
            registeredUsers[myPhone] = "Me"
            screen = "CHATS"
        }
    }

    fun addContact(name: String, phone: String, onNotFound: () -> Unit) {
        val clean = phone.trim()
        if (registeredUsers.containsKey(clean)) {
            val contactName = if (name.isNotBlank()) name else registeredUsers[clean] ?: clean
            if (myContacts.none { it.phoneNumber == clean }) {
                myContacts.add(ViaContact(contactName, clean))
            }
        } else {
            onNotFound()
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                val vm: ViaViewModel = viewModel()
                when (vm.screen) {
                    "LOGIN" -> LoginView(vm)
                    "OTP" -> OtpView(vm)
                    "CHATS" -> ChatsView(vm)
                    "ROOM" -> RoomView(vm)
                }
            }
        }
    }
}

@Composable
fun LoginView(vm: ViaViewModel) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(modifier = Modifier.size(70.dp).background(ViaTeal, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Chat, contentDescription = null, tint = Color.White, modifier = Modifier.size(36.dp))
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text("VIA APP", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = ViaDarkGreen)
        Spacer(modifier = Modifier.height(24.dp))
        OutlinedTextField(
            value = vm.inputPhone,
            onValueChange = { vm.inputPhone = it },
            label = { Text("Phone Number") },
            leadingIcon = { Icon(Icons.Default.Phone, null) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = vm.inputPassword,
            onValueChange = { vm.inputPassword = it },
            label = { Text("Password") },
            leadingIcon = { Icon(Icons.Default.Lock, null) },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = { vm.sendOtp() },
            colors = ButtonDefaults.buttonColors(containerColor = ViaTeal),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Text("Next (Get 6-Digit Code)")
        }
    }
}

@Composable
fun OtpView(vm: ViaViewModel) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Your 6-Digit Code:", fontSize = 16.sp, color = Color.Gray)
        Card(colors = CardDefaults.cardColors(containerColor = ViaOutgoingBubble), modifier = Modifier.padding(16.dp)) {
            Text(vm.generatedOtp, fontSize = 34.sp, fontWeight = FontWeight.Bold, color = ViaDarkGreen, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        }
        OutlinedTextField(value = vm.enteredOtp, onValueChange = { vm.enteredOtp = it }, label = { Text("Enter 6-Digit Code") }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(20.dp))
        Button(onClick = { vm.verify() }, colors = ButtonDefaults.buttonColors(containerColor = ViaTeal), modifier = Modifier.fillMaxWidth().height(48.dp)) {
            Text("Verify & Enter")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatsView(vm: ViaViewModel) {
    var showAddDialog by remember { mutableStateOf(false) }
    var showAlert by remember { mutableStateOf(false) }
    var addName by remember { mutableStateOf("") }
    var addPhone by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("VIA APP", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ViaDarkGreen, titleContentColor = Color.White)
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }, containerColor = ViaTeal, contentColor = Color.White) {
                Icon(Icons.Default.PersonAdd, contentDescription = null)
            }
        }
    ) { pad ->
        Column(modifier = Modifier.fillMaxSize().padding(pad)) {
            if (vm.myContacts.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No contacts yet.\nTap + to add a registered phone number!", color = Color.Gray)
                }
            } else {
                LazyColumn {
                    items(vm.myContacts) { contact ->
                        ListItem(
                            headlineContent = { Text(contact.name, fontWeight = FontWeight.Bold) },
                            supportingContent = { Text(contact.phoneNumber) },
                            leadingContent = {
                                Box(modifier = Modifier.size(40.dp).background(Color(0xFFDDDDDD), CircleShape), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Person, contentDescription = null, tint = Color.Gray)
                                }
                            },
                            modifier = Modifier.clickable {
                                vm.activeContact = contact
                                vm.screen = "ROOM"
                            }
                        )
                        HorizontalDivider(color = Color(0xFFF2F2F2))
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add User") },
            text = {
                Column {
                    OutlinedTextField(value = addName, onValueChange = { addName = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = addPhone, onValueChange = { addPhone = it }, label = { Text("Phone Number") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        vm.addContact(addName, addPhone) { showAlert = true }
                        showAddDialog = false
                        addName = ""
                        addPhone = ""
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ViaTeal)
                ) {
                    Text("Add")
                }
            }
        )
    }

    if (showAlert) {
        AlertDialog(
            onDismissRequest = { showAlert = false },
            title = { Text("User Not Found") },
            text = { Text("User not in the VIA APP.\nThis phone number has not registered yet.") },
            confirmButton = {
                Button(onClick = { showAlert = false }, colors = ButtonDefaults.buttonColors(containerColor = ViaDarkGreen)) {
                    Text("OK")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomView(vm: ViaViewModel) {
    var txt by remember { mutableStateOf("") }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(vm.activeContact?.name ?: "Chat") },
                navigationIcon = {
                    IconButton(onClick = { vm.screen = "CHATS" }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ViaDarkGreen, titleContentColor = Color.White)
            )
        }
    ) { pad ->
        Column(modifier = Modifier.fillMaxSize().padding(pad).background(ViaChatBackground)) {
            LazyColumn(modifier = Modifier.weight(1f).padding(12.dp)) {
                items(vm.messages) { msg ->
                    val isMe = msg.senderPhone == vm.myPhone
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), contentAlignment = if (isMe) Alignment.CenterEnd else Alignment.CenterStart) {
                        Surface(color = if (isMe) ViaOutgoingBubble else Color.White, shape = RoundedCornerShape(8.dp), shadowElevation = 1.dp) {
                            Text(msg.text, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), fontSize = 15.sp)
                        }
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = txt, onValueChange = { txt = it }, placeholder = { Text("Message") }, shape = RoundedCornerShape(24.dp), modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.width(6.dp))
                IconButton(
                    onClick = {
                        if (txt.isNotBlank()) {
                            vm.messages.add(ViaMessage(vm.myPhone, txt))
                            txt = ""
                        }
                    },
                    modifier = Modifier.background(ViaTeal, CircleShape)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = Color.White)
                }
            }
        }
    }
}