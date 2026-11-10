package com.example.viaapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.DoneAll
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
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

val ViaTeal = Color(0xFF00A884)
val ViaDarkGreen = Color(0xFF008069)
val ViaChatBackground = Color(0xFFEFE7DE)
val ViaOutgoingBubble = Color(0xFFD9FDD3)

data class ViaContact(val name: String, val phoneNumber: String)
data class ViaMessage(val senderPhone: String, val text: String, val timestamp: Long)

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
    var isChecking by mutableStateOf(false)

    val myContacts = mutableStateListOf<ViaContact>()
    val conversationMessages = mutableStateListOf<ViaMessage>()
    var activeContact by mutableStateOf<ViaContact?>(null)

    private var pollingJob: Job? = null

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    // 100% Free Open Real-time Relay (Requires NO registration or API keys)
    private val RELAY_URL = "https://ntfy.sh/"

    fun registerOrLogin() {
        val cleanPhone = inputPhone.trim()
        val cleanName = inputName.trim()
        val cleanPass = inputPassword.trim()

        if (cleanName.isBlank()) { errorMessage = "Please enter your username"; return }
        if (cleanPhone.length < 10) { errorMessage = "Please enter a valid phone number"; return }
        if (cleanPass.length != 6 || !cleanPass.all { it.isDigit() }) {
            errorMessage = "Password must be exactly 6 digits"; return
        }

        isChecking = true
        errorMessage = null

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Check if this phone number already registered on the relay
                val req = Request.Builder().url(RELAY_URL + "via_usr_" + cleanPhone + "/json?poll=1").get().build()
                val resp = client.newCall(req).execute()
                val body = resp.body?.string() ?: ""

                var existingPass: String? = null
                var existingName: String? = null

                // Parse ntfy response lines
                for (line in body.lines()) {
                    if (line.trim().isBlank()) continue
                    try {
                        val json = JSONObject(line)
                        if (json.optString("event") == "message") {
                            val payload = JSONObject(json.optString("message"))
                            existingPass = payload.optString("pass")
                            existingName = payload.optString("name")
                        }
                    } catch (e: Exception) { /* continue */ }
                }

                withContext(Dispatchers.Main) {
                    isChecking = false
                    if (existingPass != null) {
                        // User already exists!
                        if (existingPass == cleanPass) {
                            myPhone = cleanPhone
                            myName = existingName ?: cleanName
                            screen = "CHATS"
                        } else {
                            errorMessage = "Number already registered with another password."
                        }
                    } else {
                        // New user: proceed to 6-digit confirmation
                        generatedOtp = (100000..999999).random().toString()
                        screen = "OTP"
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isChecking = false
                    generatedOtp = (100000..999999).random().toString()
                    screen = "OTP"
                }
            }
        }
    }

    fun verify() {
        if (enteredOtp.trim() == generatedOtp) {
            val cleanPhone = inputPhone.trim()
            val cleanName = inputName.trim()
            val cleanPass = inputPassword.trim()

            myPhone = cleanPhone
            myName = cleanName

            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val userObj = JSONObject().apply {
                        put("name", cleanName)
                        put("phone", cleanPhone)
                        put("pass", cleanPass)
                    }
                    val body = userObj.toString().toRequestBody("text/plain".toMediaType())
                    val saveReq = Request.Builder().url(RELAY_URL + "via_usr_" + cleanPhone).post(body).build()
                    client.newCall(saveReq).execute()
                } catch (e: Exception) { e.printStackTrace() }
            }

            screen = "CHATS"
            errorMessage = null
        } else {
            errorMessage = "Invalid 6-digit confirmation code."
        }
    }

    /**
     * SEARCH CLOUD DATABASE TO FIND PHONE B
     */
    fun addContact(name: String, phone: String, onNotFound: () -> Unit) {
        val clean = phone.trim()
        if (clean.isBlank()) return

        isChecking = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val req = Request.Builder().url(RELAY_URL + "via_usr_" + clean + "/json?poll=1").get().build()
                val resp = client.newCall(req).execute()
                val body = resp.body?.string() ?: ""

                var foundName: String? = null

                for (line in body.lines()) {
                    if (line.trim().isBlank()) continue
                    try {
                        val json = JSONObject(line)
                        if (json.optString("event") == "message") {
                            val payload = JSONObject(json.optString("message"))
                            foundName = payload.optString("name")
                        }
                    } catch (e: Exception) { /* continue */ }
                }

                withContext(Dispatchers.Main) {
                    isChecking = false
                    if (foundName != null) {
                        val displayName = if (name.isNotBlank()) name.trim() else foundName
                        if (myContacts.none { it.phoneNumber == clean }) {
                            myContacts.add(ViaContact(displayName, clean))
                        }
                    } else {
                        onNotFound()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isChecking = false
                    onNotFound()
                }
            }
        }
    }

    fun openChat(contact: ViaContact) {
        activeContact = contact
        conversationMessages.clear()
        screen = "ROOM"
        startLiveMessages()
    }

    fun closeChat() {
        pollingJob?.cancel()
        activeContact = null
        screen = "CHATS"
    }

    fun sendMessage(txt: String) {
        val recipient = activeContact?.phoneNumber ?: return
        val cleanTxt = txt.trim()
        if (cleanTxt.isBlank()) return

        val now = System.currentTimeMillis()
        val newMsg = ViaMessage(myPhone, cleanTxt, now)
        conversationMessages.add(newMsg)

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val channelKey = getChannelKey(myPhone, recipient)
                val msgObj = JSONObject().apply {
                    put("sender", myPhone)
                    put("text", cleanTxt)
                    put("time", now)
                }

                val body = msgObj.toString().toRequestBody("text/plain".toMediaType())
                val req = Request.Builder().url(RELAY_URL + channelKey).post(body).build()
                client.newCall(req).execute()
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private fun startLiveMessages() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                val recipient = activeContact?.phoneNumber
                if (recipient != null && myPhone.isNotBlank()) {
                    try {
                        val channelKey = getChannelKey(myPhone, recipient)
                        val req = Request.Builder().url(RELAY_URL + channelKey + "/json?poll=1").get().build()
                        val resp = client.newCall(req).execute()
                        val body = resp.body?.string() ?: ""

                        val fetched = mutableListOf<ViaMessage>()
                        for (line in body.lines()) {
                            if (line.trim().isBlank()) continue
                            try {
                                val json = JSONObject(line)
                                if (json.optString("event") == "message") {
                                    val payload = JSONObject(json.optString("message"))
                                    fetched.add(
                                        ViaMessage(
                                            senderPhone = payload.optString("sender"),
                                            text = payload.optString("text"),
                                            timestamp = payload.optLong("time")
                                        )
                                    )
                                }
                            } catch (e: Exception) { /* continue */ }
                        }

                        withContext(Dispatchers.Main) {
                            if (fetched.size != conversationMessages.size) {
                                conversationMessages.clear()
                                conversationMessages.addAll(fetched)
                            }
                        }
                    } catch (e: Exception) { /* retry */ }
                }
                delay(2000)
            }
        }
    }

    private fun getChannelKey(p1: String, p2: String): String {
        return if (p1 < p2) "via_chat_${p1}_$p2" else "via_chat_${p2}_$p1"
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

        OutlinedTextField(
            value = vm.inputName,
            onValueChange = { vm.inputName = it },
            label = { Text("Username") },
            leadingIcon = { Icon(Icons.Default.Person, null) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = vm.inputPhone,
            onValueChange = { vm.inputPhone = it },
            label = { Text("Phone Number") },
            leadingIcon = { Icon(Icons.Default.Phone, null) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(10.dp))

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
                    Icon(image, contentDescription = null)
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
            enabled = !vm.isChecking,
            colors = ButtonDefaults.buttonColors(containerColor = ViaTeal),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Text(if (vm.isChecking) "Connecting..." else "Next / Sign In")
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
                                vm.openChat(contact)
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
                    OutlinedTextField(value = addName, onValueChange = { addName = it }, label = { Text("Contact Name (Optional)") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = addPhone,
                        onValueChange = { addPhone = it },
                        label = { Text("Phone Number") },
                        placeholder = { Text("e.g. 08012345678") },
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
                ) { Text("Search & Add") }
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

// --- WHATSAPP-STYLE CHAT ROOM (MESSAGES AT THE BOTTOM) ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomView(vm: ViaViewModel) {
    var txt by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(vm.conversationMessages.size) {
        if (vm.conversationMessages.isNotEmpty()) {
            listState.animateScrollToItem(vm.conversationMessages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(vm.activeContact?.name ?: "Chat") },
                navigationIcon = {
                    IconButton(onClick = { vm.closeChat() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ViaDarkGreen, titleContentColor = Color.White)
            )
        }
    ) { pad ->
        Column(modifier = Modifier.fillMaxSize().padding(pad).background(ViaChatBackground)) {

            // Arrangement.Bottom keeps messages at the bottom like WhatsApp!
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.Bottom,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                items(vm.conversationMessages) { msg ->
                    val isMe = msg.senderPhone == vm.myPhone
                    val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
                    val formattedTime = timeFormat.format(Date(msg.timestamp))

                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        contentAlignment = if (isMe) Alignment.CenterEnd else Alignment.CenterStart
                    ) {
                        Surface(
                            color = if (isMe) ViaOutgoingBubble else Color.White,
                            shape = RoundedCornerShape(8.dp),
                            shadowElevation = 1.dp
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                                Text(
                                    text = msg.text,
                                    fontSize = 15.sp,
                                    color = Color.Black
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(
                                    modifier = Modifier.align(Alignment.End),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = formattedTime,
                                        fontSize = 10.sp,
                                        color = Color.Gray
                                    )
                                    if (isMe) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            Icons.Default.DoneAll,
                                            contentDescription = null,
                                            tint = ViaDarkGreen,
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Bottom Typing Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = txt,
                    onValueChange = { txt = it },
                    placeholder = { Text("Message") },
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White
                    ),
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