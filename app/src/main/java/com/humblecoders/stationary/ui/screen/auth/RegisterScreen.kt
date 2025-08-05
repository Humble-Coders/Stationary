package com.humblecoders.stationary.ui.screen.auth

import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.humblecoders.stationary.ui.viewmodel.auth.RegisterViewModel
import com.humblecoders.stationary.ui.viewmodel.auth.RegisterState
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(
    viewModel: RegisterViewModel,
    navController: NavController,
    googleSignInLauncher: ActivityResultLauncher<Intent>
) {
    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var isConfirmPasswordVisible by remember { mutableStateOf(false) }
    val registerState by viewModel.registerState.collectAsState()
    val focusManager = LocalFocusManager.current
    var phone by remember { mutableStateOf("") }

    var passwordError by remember { mutableStateOf<String?>(null) }
    var confirmPasswordError by remember { mutableStateOf<String?>(null) }
    var emailError by remember { mutableStateOf<String?>(null) }
    var nameError by remember { mutableStateOf<String?>(null) }
    var phoneError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(registerState) {
        when (registerState) {
            is RegisterState.Success -> {
                delay(300)
                navController.navigate("home") {
                    popUpTo("register") { inclusive = true }
                }
                viewModel.resetState()
            }
            else -> {
            }
        }
    }

    Scaffold { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .background(MaterialTheme.colorScheme.background)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 16.dp)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {
                        focusManager.clearFocus()
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                BrandHeader()
                CreateAccountHeader()

                FullNameInput(
                    fullName = fullName,
                    onFullNameChange = {
                        fullName = it
                        nameError = if (it.isBlank()) "Name is required" else null
                    },
                    error = nameError
                )

                Spacer(modifier = Modifier.height(10.dp))

                EmailInput(
                    email = email,
                    onEmailChange = {
                        email = it
                        emailError = if (it.isBlank()) {
                            "Email is required"
                        } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(it).matches()) {
                            "Please enter a valid email"
                        } else {
                            null
                        }
                    },
                    error = emailError
                )

                Spacer(modifier = Modifier.height(10.dp))

                PhoneInput(
                    phone = phone,
                    onPhoneChange = {
                        phone = it
                        phoneError = if (it.isNotEmpty() && !isValidPhone(it)) {
                            "Please enter a valid phone number"
                        } else {
                            null
                        }
                    },
                    error = phoneError
                )

                Spacer(modifier = Modifier.height(10.dp))

                PasswordInput(
                    password = password,
                    isPasswordVisible = isPasswordVisible,
                    onPasswordChange = {
                        password = it
                        passwordError = if (it.length < 6) {
                            "Password must be at least 6 characters"
                        } else {
                            null
                        }

                        if (confirmPassword.isNotEmpty()) {
                            confirmPasswordError = if (it != confirmPassword) {
                                "Passwords don't match"
                            } else {
                                null
                            }
                        }
                    },
                    onTogglePasswordVisibility = { isPasswordVisible = !isPasswordVisible },
                    label = "Password",
                    placeholder = "Create your password",
                    error = passwordError
                )

                Spacer(modifier = Modifier.height(10.dp))

                PasswordInput(
                    password = confirmPassword,
                    isPasswordVisible = isConfirmPasswordVisible,
                    onPasswordChange = {
                        confirmPassword = it
                        confirmPasswordError = if (it != password) {
                            "Passwords don't match"
                        } else {
                            null
                        }
                    },
                    onTogglePasswordVisibility = { isConfirmPasswordVisible = !isConfirmPasswordVisible },
                    label = "Confirm Password",
                    placeholder = "Confirm your password",
                    imeAction = ImeAction.Done,
                    error = confirmPasswordError
                )

                Spacer(modifier = Modifier.height(8.dp))

                SignUpButton(
                    isLoading = registerState is RegisterState.Loading,
                    onClick = {
                        nameError = if (fullName.isBlank()) "Name is required" else null
                        emailError = if (email.isBlank()) {
                            "Email is required"
                        } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                            "Please enter a valid email"
                        } else {
                            null
                        }
                        passwordError = if (password.length < 6) {
                            "Password must be at least 6 characters"
                        } else {
                            null
                        }
                        confirmPasswordError = if (password != confirmPassword) {
                            "Passwords don't match"
                        } else {
                            null
                        }
                        phoneError = if (phone.isNotEmpty() && !isValidPhone(phone)) {
                            "Please enter a valid phone number"
                        } else {
                            null
                        }

                        if (nameError == null && emailError == null && phoneError == null &&
                            passwordError == null && confirmPasswordError == null) {
                            focusManager.clearFocus()
                            viewModel.registerWithEmailAndPassword(fullName, email, password, phone)
                        }
                    }
                )

                Spacer(modifier = Modifier.height(4.dp))

                AlternativeSignUpOptions(
                    isLoading = registerState is RegisterState.Loading || registerState is RegisterState.GoogleSignInLoading,
                    isGoogleLoading = registerState is RegisterState.GoogleSignInLoading,
                    onGoogleSignUpClick = {
                        try {
                            val signInIntent = viewModel.startGoogleSignIn()
                            googleSignInLauncher.launch(signInIntent)
                        } catch (e: Exception) {
                            viewModel.cancelGoogleSignIn()
                        }
                    }
                )

                Spacer(modifier = Modifier.height(4.dp))

                SignInPrompt(
                    onSignInClick = {
                        navController.popBackStack()
                    }
                )

                // Error Messages
                when (val state = registerState) {
                    is RegisterState.Error -> {
                        Spacer(modifier = Modifier.height(16.dp))
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Text(
                                text = state.message,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                    else -> {}
                }

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

private fun isValidPhone(phone: String): Boolean {
    return phone.length >= 10 && phone.all { it.isDigit() || it == '+' || it == ' ' || it == '-' || it == '(' || it == ')' }
}

@Composable
private fun CreateAccountHeader() {
    Text(
        text = "Create Account",
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
        style = androidx.compose.ui.text.TextStyle(lineHeight = 0.sp)
    )
    Text(
        text = "Please fill in your details to continue",
        fontSize = 14.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Spacer(modifier = Modifier.height(3.dp))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FullNameInput(
    fullName: String,
    onFullNameChange: (String) -> Unit,
    error: String? = null
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Full Name",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        OutlinedTextField(
            value = fullName,
            onValueChange = onFullNameChange,
            placeholder = { Text("Enter your full name") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Next
            ),
            shape = RoundedCornerShape(8.dp),
            isError = error != null
        )

        if (error != null) {
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EmailInput(
    email: String,
    onEmailChange: (String) -> Unit,
    error: String? = null
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Email",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        OutlinedTextField(
            value = email,
            onValueChange = onEmailChange,
            placeholder = { Text("Enter your email") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next
            ),
            shape = RoundedCornerShape(8.dp),
            isError = error != null
        )

        if (error != null) {
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhoneInput(
    phone: String,
    onPhoneChange: (String) -> Unit,
    error: String? = null
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Phone Number (Optional)",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        OutlinedTextField(
            value = phone,
            onValueChange = onPhoneChange,
            placeholder = { Text("Enter your phone number") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Phone,
                imeAction = ImeAction.Next
            ),
            shape = RoundedCornerShape(8.dp),
            isError = error != null
        )

        if (error != null) {
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PasswordInput(
    password: String,
    isPasswordVisible: Boolean,
    onPasswordChange: (String) -> Unit,
    onTogglePasswordVisibility: () -> Unit,
    label: String,
    placeholder: String,
    imeAction: ImeAction = ImeAction.Next,
    error: String? = null
) {
    val focusManager = LocalFocusManager.current

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        OutlinedTextField(
            value = password,
            onValueChange = onPasswordChange,
            placeholder = { Text(placeholder) },
            singleLine = true,
            visualTransformation = if (isPasswordVisible)
                VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = imeAction
            ),
            keyboardActions = KeyboardActions(
                onDone = { focusManager.clearFocus() }
            ),
            trailingIcon = {
                IconButton(onClick = onTogglePasswordVisibility) {
                    Icon(
                        imageVector = if (isPasswordVisible)
                            Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = "Toggle password visibility"
                    )
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(8.dp),
            isError = error != null
        )

        if (error != null) {
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
            )
        }
    }
}

@Composable
private fun SignUpButton(
    isLoading: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = GoldenShade
        ),
        shape = RoundedCornerShape(8.dp),
        enabled = !isLoading
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier.size(24.dp)
            )
        } else {
            Text(
                text = "Sign Up",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun AlternativeSignUpOptions(
    isLoading: Boolean,
    isGoogleLoading: Boolean,
    onGoogleSignUpClick: () -> Unit
) {
    Spacer(modifier = Modifier.height(2.dp))

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        HorizontalDivider(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
        )

        Text(
            text = "Or continue with",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        HorizontalDivider(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
        )
    }

    Spacer(modifier = Modifier.height(8.dp))

    OutlinedButton(
        onClick = onGoogleSignUpClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        colors = ButtonDefaults.outlinedButtonColors(),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shape = RoundedCornerShape(8.dp),
        enabled = !isLoading
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (isGoogleLoading) {
                CircularProgressIndicator(
                    color = GoldenShade,
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Signing in...", color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp)
            } else {
                Text("G", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = GoldenShade)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Continue with Google",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 16.sp
                )
            }
        }
    }
}

@Composable
private fun SignInPrompt(onSignInClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Already have an account? ",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "Sign In",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = GoldenShade,
            modifier = Modifier.clickable(onClick = onSignInClick)
        )
    }
}