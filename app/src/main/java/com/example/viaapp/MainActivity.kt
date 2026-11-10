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
import org.json.JSONArray
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
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    // Shared global live cloud store for VIA APP
    private val BASE_URL = "https://kvdb.io/Vj9r8k2mQ7xL5t4pW3zB1N/"

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
                val request = Request.Builder().url(BASE_URL + "user_" + cleanPhone).get().build()
                val response = client.newCall(request).execute()

                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val json = JSONObject(body)
                    val savedPass = json.optString("pass")

                    withContext(Dispatchers.Main) {
                        isChecking = false
                        if (savedPass == cleanPass) {
                            myPhone = cleanPhone
                            myName = json.optString("name", cleanName)
                            screen = "CHATS"
                        } else {
                            errorMessage = "Number already registered with another password."
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        isChecking = false
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
                    val body = userObj.toString().toRequestBody("application/json".toMediaType())
                    val saveReq = Request.Builder().url(BASE_URL + "user_" + cleanPhone).post(body).build()
                    client.newCall(saveReq).execute()
                } catch (e: Exception) { e.printStackTrace() }
            }

            screen = "CHATS"
            errorMessage = null
        } else {
            errorMessage = "Invalid 6-digit code."
        }
    }

    fun addContact(name: String, phone: String, onNotFound: () -> Unit) {
        val clean = phone.trim()
        if (clean.isBlank()) return

        isChecking = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val req = Request.Builder().url(BASE_URL + "user_" + clean).get().build()
                val resp = client.newCall(req).execute()

                withContext(Dispatchers.Main) {
                    isChecking = false
                    if (resp.isSuccessful) {
                        val body = resp.body?.string() ?: ""
                        val json = JSONObject(body)
                        val cloudName = json.optString("name", clean)
                        val displayName = if (name.isNotBlank()) name.trim() else cloudName

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

        val newMsg = ViaMessage(myPhone, cleanTxt, System.currentTimeMillis())
        conversationMessages.add(newMsg)

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val channelKey = getChannelKey(myPhone, recipient)
                val msgObj = JSONObject().apply {
                    put("sender", myPhone)
                    put("text", cleanTxt)
                    put("time", System.currentTimeMillis())
                }

                val getReq = Request.Builder().url(BASE_URL + channelKey).get().build()
                val getResp = client.newCall(getReq).execute()
                val array = if (getResp.isSuccessful) {
                    JSONArray(getResp.body?.string() ?: "[]")
                } else {
                    JSONArray()
                }
                array.put(msgObj)

                val postBody = array.toString().toRequestBody("application/json".toMediaType())
                val postReq = Request.Builder().url(BASE_URL + channelKey).post(postBody).build()
                client.newCall(postReq).execute()
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
                        val req = Request.Builder().url(BASE_URL + channelKey).get().build()
                        val resp = client.newCall(req).execute()

                        if (resp.isSuccessful) {
                            val array = JSONArray(resp.body?.string() ?: "[]")
                            val fetched = mutableListOf<ViaMessage>()
                            for (i in 0 until array.length()) {
                                val item = array.getJSONObject(i)
                                fetched.add(
                                    ViaMessage(
                                        senderPhone = item.optString("sender"),
                                        text = item.optString("text"),
                                        timestamp = item.optLong("time")
                                    )
                                )
                            }
                            withContext(Dispatchers.Main) {
                                if (fetched.size != conversationMessages.size) {
                                    conversationMessages.clear()
                                    conversationMessages.addAll(fetched)
                                }
                            }
                        }
                    } catch (e: Exception) { /* retry safely */ }
                }
                delay(2000)
            }
        }
    }

    private fun getChannelKey(p1: String, p2: String): String {
        return if (p1 < p2) "chat_${p1}_$p2" else "chat_${p2}_$p1"
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

    // Automatically scroll to the latest message at the bottom when sent or received
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

            // LazyColumn with Arrangement.Bottom: Keeps messages sitting at the BOTTOM like WhatsApp!
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