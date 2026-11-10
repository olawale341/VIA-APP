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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel

val ViaTeal = Color(0xFF00A884)
val ViaDarkGreen = Color(0xFF008069)
val ViaChatBackground = Color(0xFFEFE7DE)
val ViaOutgoingBubble = Color(0xFFD9FDD3)

data class ViaContact(val name: String, val phoneNumber: String)
data class ViaMessage(val senderPhone: String, val recipientPhone: String, val text: String, val timestamp: Long)

class ViaViewModel : ViewModel() {
    var screen by mutableStateOf("LOGIN")
    var inputName by mutableStateOf("")
    var inputPhone by mutableStateOf("")
    var inputPassword by mutableStateOf("")
    var generatedOtp by mutableStateOf("")
    var enteredOtp by mutableStateOf("")
    var myPhone by mutableStateOf("")
    var myName by mutableStateOf("")
    var errorMessage by mutableStateOf<String?>(null)

    // Registered database: phoneNumber -> Pair(Name, 6-digit Password)
    val registeredAccounts = mutableStateMapOf(
        "08012345678" to Pair("Brother John", "123456"),
        "09087654321" to Pair("Musa Repairs", "654321")
    )

    val myContacts = mutableStateListOf<ViaContact>()
    val allMessages = mutableStateListOf<ViaMessage>()
    var activeContact by mutableStateOf<ViaContact?>(null)

    fun registerOrLogin() {
        val cleanPhone = inputPhone.trim()
        val cleanName = inputName.trim()
        val cleanPass = inputPassword.trim()

        if (cleanName.isBlank()) {
            errorMessage = "Please enter your username"
            return
        }
        if (cleanPhone.length < 10) {
            errorMessage = "Please enter a valid phone number"
            return
        }
        if (cleanPass.length != 6 || !cleanPass.all { it.isDigit() }) {
            errorMessage = "Password must be exactly 6 digits"
            return
        }

        // ONE REGISTER PER ACCOUNT CHECK
        if (registeredAccounts.containsKey(cleanPhone)) {
            val existing = registeredAccounts[cleanPhone]!!
            if (existing.second == cleanPass) {
                // Legitimate login for existing user
                myPhone = cleanPhone
                myName = existing.first
                screen = "CHATS"
                errorMessage = null
                return
            } else {
                errorMessage = "Number already registered with a different password."
                return
            }
        }

        // Fresh Registration: Generate instant 6-digit verification code
        generatedOtp = (100000..999999).random().toString()
        errorMessage = null
        screen = "OTP"
    }

    fun verify() {
        if (enteredOtp.trim() == generatedOtp) {
            val cleanPhone = inputPhone.trim()
            val cleanName = inputName.trim()
            val cleanPass = inputPassword.trim()

            myPhone = cleanPhone
            myName = cleanName
            registeredAccounts[cleanPhone] = Pair(cleanName, cleanPass)
            screen = "CHATS"
            errorMessage = null
        } else {
            errorMessage = "Invalid 6-digit confirmation code."
        }
    }

    fun addContact(name: String, phone: String, onNotFound: () -> Unit) {
        val clean = phone.trim()
        if (registeredAccounts.containsKey(clean)) {
            val registeredName = registeredAccounts[clean]!!.first
            val contactName = if (name.isNotBlank()) name.trim() else registeredName
            if (myContacts.none { it.phoneNumber == clean }) {
                myContacts.add(ViaContact(contactName, clean))
            }
        } else {
            onNotFound()
        }
    }

    fun sendMessage(txt: String) {
        val recipient = activeContact?.phoneNumber ?: return
        if (txt.isNotBlank()) {
            allMessages.add(
                ViaMessage(
                    senderPhone = myPhone,
                    recipientPhone = recipient,
                    text = txt.trim(),
                    timestamp = System.currentTimeMillis()
                )
            )
        }
    }

    // Filters messages strictly between You and the Active Chat Contact
    fun getMessagesForActiveContact(): List<ViaMessage> {
        val recipient = activeContact?.phoneNumber ?: return emptyList()
        return allMessages.filter {
            (it.senderPhone == myPhone && it.recipientPhone == recipient) ||
            (it.senderPhone == recipient && it.recipientPhone == myPhone)
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
    var passwordVisible by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(modifier = Modifier.size(70.dp).background(ViaTeal, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Chat, contentDescription = null, tint = Color.White, modifier = Modifier.size(36.dp))
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text("VIA APP", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = ViaDarkGreen)
        Text("Connect freely by phone number", fontSize = 13.sp, color = Color.Gray)
        Spacer(modifier = Modifier.height(24.dp))

        // Username Field
        OutlinedTextField(
            value = vm.inputName,
            onValueChange = { vm.inputName = it },
            label = { Text("Username") },
            leadingIcon = { Icon(Icons.Default.Person, null) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(10.dp))

        // Phone Number Field
        OutlinedTextField(
            value = vm.inputPhone,
            onValueChange = { vm.inputPhone = it },
            label = { Text("Phone Number") },
            leadingIcon = { Icon(Icons.Default.Phone, null) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(10.dp))

        // 6-Digit Password with See/Hide Toggle
        OutlinedTextField(
            value = vm.inputPassword,
            onValueChange = { if (it.length <= 6) vm.inputPassword = it },
            label = { Text("6-Digit Password") },
            leadingIcon = { Icon(Icons.Default.Lock, null) },
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            trailingIcon = {
                val image = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(image, contentDescription = if (passwordVisible) "Hide password" else "Show password")
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        if (vm.errorMessage != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(vm.errorMessage!!, color = Color.Red, fontSize = 12.sp)
        }

        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = { vm.registerOrLogin() },
            colors = ButtonDefaults.buttonColors(containerColor = ViaTeal),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Text("Next / Sign In")
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
        Text("Confirm Registration", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(6.dp))
        Text("Your 6-Digit confirmation code:", fontSize = 14.sp, color = Color.Gray)

        Card(colors = CardDefaults.cardColors(containerColor = ViaOutgoingBubble), modifier = Modifier.padding(16.dp)) {
            Text(vm.generatedOtp, fontSize = 34.sp, fontWeight = FontWeight.Bold, color = ViaDarkGreen, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        }

        OutlinedTextField(
            value = vm.enteredOtp,
            onValueChange = { if (it.length <= 6) vm.enteredOtp = it },
            label = { Text("Enter 6-Digit Code") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        if (vm.errorMessage != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(vm.errorMessage!!, color = Color.Red, fontSize = 12.sp)
        }

        Spacer(modifier = Modifier.height(20.dp))
        Button(onClick = { vm.verify() }, colors = ButtonDefaults.buttonColors(containerColor = ViaTeal), modifier = Modifier.fillMaxWidth().height(48.dp)) {
            Text("Verify & Enter VIA")
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
                title = { Text("VIA APP (${vm.myName})", fontWeight = FontWeight.Bold) },
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
                    Text("No chats yet.\nTap + to add a registered phone number!", color = Color.Gray)
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
                    OutlinedTextField(value = addName, onValueChange = { addName = it }, label = { Text("Contact Name") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = addPhone,
                        onValueChange = { addPhone = it },
                        label = { Text("Phone Number") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth()
                    )
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
                ) { Text("Add") }
            }
        )
    }

    if (showAlert) {
        AlertDialog(
            onDismissRequest = { showAlert = false },
            title = { Text("User Not Found") },
            text = { Text("User not in the VIA APP.\nThis phone number has not registered yet.") },
            confirmButton = { Button(onClick = { showAlert = false }, colors = ButtonDefaults.buttonColors(containerColor = ViaDarkGreen)) { Text("OK") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomView(vm: ViaViewModel) {
    var txt by remember { mutableStateOf("") }
    val conversationMessages = vm.getMessagesForActiveContact()

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
                items(conversationMessages) { msg ->
                    val isMe = msg.senderPhone == vm.myPhone
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), contentAlignment = if (isMe) Alignment.CenterEnd else Alignment.CenterStart) {
                        Surface(color = if (isMe) ViaOutgoingBubble else Color.White, shape = RoundedCornerShape(8.dp), shadowElevation = 1.dp) {
                            Text(msg.text, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), fontSize = 15.sp)
                        }
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = txt,
                    onValueChange = { txt = it },
                    placeholder = { Text("Message") },
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(6.dp))
                IconButton(
                    onClick = {
                        if (txt.isNotBlank()) {
                            vm.sendMessage(txt)
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