const {onCall} = require("firebase-functions/v2/https");
const {onDocumentUpdated} = require("firebase-functions/v2/firestore");
const {setGlobalOptions} = require("firebase-functions/v2");
const {defineSecret} = require("firebase-functions/params");
const admin = require("firebase-admin");
const Razorpay = require("razorpay");
const crypto = require("crypto");
const {getStorage} = require("firebase-admin/storage");

const WEBSITE_LIMITS = {
  MAX_UPLOADS: 10,
  WINDOW_MS: 30 * 60 * 1000,
  COOLDOWN_MS: 10 * 1000,
};

// Set global options
setGlobalOptions({
  region: "us-central1",
  memory: "256MiB",
  serviceAccount: "stationary-16708@appspot.gserviceaccount.com",
});


admin.initializeApp();
const db = admin.firestore();

// ============================================
// RAZORPAY CONFIGURATION (Using Secret Manager)
// ============================================

// Define secrets - these are securely stored in Firebase Secret Manager
const razorpayKeyId = defineSecret("RAZORPAY_KEY_ID");
const razorpayKeySecret = defineSecret("RAZORPAY_KEY_SECRET");

// Note: Razorpay instance is created inside functions that need it
// because secrets are only available at runtime, not at deploy time

// ============================================
// HELPER FUNCTIONS
// ============================================

/**
 * Generate unique order ID
 * @return {string} Generated order ID
 */
function generateOrderId() {
  return "ORD" + Date.now() +
    Math.random().toString(36).substr(2, 9).toUpperCase();
}

/**
 * Parse page range string and return count of pages
 * Supports formats: "1,2,3" or "1-5" or "1,2,5-10"
 * @param {string} pageRange - Page range string
 * @return {number} Count of pages
 */
function parsePageRangeCount(pageRange) {
  if (!pageRange || pageRange.trim() === "") {
    return 0;
  }

  try {
    const pages = new Set();
    const parts = pageRange.split(",");

    for (const part of parts) {
      const trimmed = part.trim();
      if (trimmed === "") continue; // Skip empty parts

      if (trimmed.includes("-")) {
        const range = trimmed.split("-");
        if (range.length === 2) {
          const start = parseInt(range[0].trim(), 10);
          const end = parseInt(range[1].trim(), 10);

          // Skip if NaN (invalid input)
          if (isNaN(start) || isNaN(end)) {
            console.warn("Invalid page range (NaN detected):", trimmed);
            continue;
          }

          const validStart = Math.max(1, start);
          const validEnd = Math.max(1, end);

          if (validStart <= validEnd) {
            for (let i = validStart; i <= validEnd; i++) {
              pages.add(i);
            }
          }
        }
      } else {
        const page = parseInt(trimmed, 10);

        // Skip if NaN (invalid input)
        if (isNaN(page)) {
          console.warn("Invalid page number (NaN detected):", trimmed);
          continue;
        }

        if (page >= 1) {
          pages.add(page);
        }
      }
    }

    return pages.size;
  } catch (e) {
    console.error("Error parsing page range:", pageRange, e);
    return 0;
  }
}

/**
 * Calculate order price (server-side validation)
 * Uses only customBWPages and customColorPages from individualDocuments array
 * @param {Array} documents - Array of document objects with printSettings
 * @param {Object} shopSettings - Shop settings object with pricing
 * @return {number} Calculated price
 */
function calculateOrderPrice(documents, shopSettings) {
  let totalPrice = 0;

  console.log("=== CALCULATE ORDER PRICE ===");
  console.log("Shop settings received:", JSON.stringify(shopSettings));
  console.log("Shop settings keys:", Object.keys(shopSettings || {}));
  console.log("Documents count:", documents ? documents.length : 0);

  if (!shopSettings || !shopSettings.pricing) {
    console.error("shopSettings.pricing is undefined");
    throw new Error("Shop pricing configuration not found");
  }

  const pricing = shopSettings.pricing;
  console.log("Pricing object:", JSON.stringify(pricing));
  console.log("BW price:", pricing.bw, "Color price:", pricing.color);

  if (!documents || !Array.isArray(documents) || documents.length === 0) {
    console.error("Documents array is empty or invalid");
    throw new Error("Documents array is required");
  }

  documents.forEach((doc, index) => {
    console.log(`\n--- Processing document ${index + 1} ---`);
    console.log("Document:", JSON.stringify(doc));

    if (!doc.printSettings) {
      console.error(`Document ${index + 1} missing printSettings`);
      throw new Error(`Document ${index + 1} missing printSettings`);
    }

    const settings = doc.printSettings;

    // Validate copies: ensure it's a positive integer (at least 1)
    let copies = parseInt(settings.copies, 10);
    if (isNaN(copies) || copies < 1) {
      copies = 1;
    }
    // Cap maximum copies to prevent abuse (e.g., max 100)
    copies = Math.min(copies, 100);

    console.log("Print settings:", JSON.stringify(settings));
    console.log("Copies:", copies);

    // Parse page ranges
    const customBWPages = settings.customBWPages || "";
    const customColorPages = settings.customColorPages || "";

    console.log("customBWPages string:", customBWPages);
    console.log("customColorPages string:", customColorPages);

    const bwPageCount = parsePageRangeCount(customBWPages);
    const colorPageCount = parsePageRangeCount(customColorPages);

    console.log("BW pages count:", bwPageCount);
    console.log("Color pages count:", colorPageCount);

    const bwPrice = pricing.bw || 0;
    const colorPrice = pricing.color || 0;

    const bwCost = bwPageCount * bwPrice;
    const colorCost = colorPageCount * colorPrice;

    console.log("BW cost:", bwCost, "(pages:", bwPageCount,
        "x price:", bwPrice, ")");
    console.log("Color cost:", colorCost, "(pages:", colorPageCount,
        "x price:", colorPrice, ")");

    const docPrice = (bwCost + colorCost) * copies;
    console.log("Document price:", docPrice, "(before copies:",
        bwCost + colorCost, "x copies:", copies, ")");

    totalPrice += docPrice;
  });

  console.log("\n=== TOTAL CALCULATED PRICE ===");
  console.log("Total price:", totalPrice);
  return totalPrice;
}


exports.createWebsitePrintOrder = onCall(async (request) => {
  try {
    console.log("=== WEBSITE CREATE PRINT ORDER ===");

    if (!request.auth) {
      throw new Error("Unauthenticated website request");
    }

    const uid = request.auth.uid;
    const payload = request.data.data || request.data;

    const {documents, customerPhone, customerName, shopId} = payload;

    if (!documents || !Array.isArray(documents) || documents.length === 0) {
      throw new Error("Documents array is required");
    }

    if (!shopId || (shopId !== "GBLOCK" && shopId !== "COS")) {
      throw new Error("Invalid shop ID");
    }

    const tempUserRef = db.collection("temp_users").doc(uid);
    const now = Date.now();

    await db.runTransaction(async (tx) => {
      const snap = await tx.get(tempUserRef);

      if (!snap.exists) {
        tx.set(tempUserRef, {
          uploadCount: 1,
          windowStart: now,
          lastUploadAt: now,
          createdAt: admin.firestore.FieldValue.serverTimestamp(),
        });
        return;
      }

      const d = snap.data();

      if (now - d.lastUploadAt < WEBSITE_LIMITS.COOLDOWN_MS) {
        throw new Error("UPLOAD_COOLDOWN");
      }

      if (now - d.windowStart > WEBSITE_LIMITS.WINDOW_MS) {
        tx.set(tempUserRef, {
          uploadCount: 1,
          windowStart: now,
          lastUploadAt: now,
        });
        return;
      }

      if (d.uploadCount >= WEBSITE_LIMITS.MAX_UPLOADS) {
        throw new Error("UPLOAD_LIMIT_REACHED");
      }

      tx.update(tempUserRef, {
        uploadCount: d.uploadCount + 1,
        lastUploadAt: now,
      });
    });

    // Get shop-specific settings (includes isShopOpen and pricing)
    const shopSettingsDoc = await db
        .collection("shop_settings")
        .doc(shopId)
        .get();

    if (!shopSettingsDoc.exists) {
      throw new Error(`Shop settings not found for ${shopId}`);
    }

    const shopSettings = shopSettingsDoc.data();

    if (!shopSettings.isShopOpen) {
      throw new Error(`${shopId === "GBLOCK" ? "GBlock" : "Cos"} shop closed`);
    }

    const calculatedPrice = calculateOrderPrice(documents, shopSettings);
    const orderId = generateOrderId();

    const bucket = getStorage().bucket();
    // Return just the order directory path, not including any filename
    const uploadPath = `website_uploads/${orderId}`;

    // Generate signed URLs for each document
    const individualDocuments = await Promise.all(
        documents.map(async (doc) => {
          const docFilePath = `${uploadPath}/${doc.fileName}`;
          const file = bucket.file(docFilePath);

          // Generate signed URL valid for 7 days
          const [signedUrl] = await file.getSignedUrl({
            action: "read",
            expires: Date.now() + 7 * 24 * 60 * 60 * 1000,
          });

          return {
            fileName: doc.fileName,
            fileType: doc.fileType,
            fileUrl: signedUrl,
            printSettings: {
              customBWPages: doc.printSettings.customBWPages || "",
              customColorPages: doc.printSettings.customColorPages || "",
              copies: doc.printSettings.copies || 1,
              printOnBothSides: doc.printSettings.printOnBothSides ?
                "true" : "false",
              orientation: doc.printSettings.orientation || "PORTRAIT",
            },
          };
        }),
    );

    const orderData = {
      orderId,
      customerId: uid,
      customerPhone: customerPhone || "",
      customerName: customerName || "",
      shopId: shopId,
      individualDocuments,
      documentCount: individualDocuments.length,
      paymentStatus: "NOT_REQUIRED",
      paymentAmount: calculatedPrice,
      orderStatus: "PENDING",
      source: "WEBSITE",
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    };

    await db.collection("print_orders").doc(orderId).set(orderData);

    console.log("Website order created:", orderId);

    return {
      success: true,
      orderId,
      uploadPath: uploadPath, // Just the directory: "website_uploads/ORD..."
    };
  } catch (error) {
    console.error("Website order error:", error.message);
    throw error;
  }
});


// ============================================
// CLOUD FUNCTION 1: CREATE ORDER
// ============================================

exports.createOrder = onCall(async (request) => {
  try {
    console.log("=== CREATE ORDER CALLED ===");

    // Authentication check
    if (!request.auth) {
      throw new Error("Unauthenticated");
    }

    const payload = request.data.data || request.data;

    console.log("Received data keys:", Object.keys(request.data));
    console.log("Payload keys:", Object.keys(payload));
    console.log("customerId:", payload.customerId);
    console.log(
        "documents count:",
        payload.documents ? payload.documents.length : 0,
    );
    console.log("totalAmount:", payload.totalAmount);
    console.log("customerPhone:", payload.customerPhone);

    // Use authenticated user ID instead of trusting payload
    const userId = request.auth.uid;

    // Verify customerId matches authenticated user (if provided)
    if (payload.customerId && payload.customerId !== userId) {
      throw new Error("Unauthorized: customerId mismatch");
    }

    console.log("Processing order for user:", userId);

    // ============================================
    // RATE LIMITING: Max 10 orders per 30 minutes
    // ============================================
    const userRef = db.collection("users").doc(userId);
    const userDoc = await userRef.get();

    const now = Date.now();
    const thirtyMinutesMs = 30 * 60 * 1000;

    let orderRateLimit = {
      count: 0,
      windowStart: now,
    };

    if (userDoc.exists && userDoc.data().orderRateLimit) {
      const existingLimit = userDoc.data().orderRateLimit;
      // Handle both Firestore Timestamp and plain number
      let windowStart = 0;
      if (existingLimit.windowStart) {
        if (typeof existingLimit.windowStart.toMillis === "function") {
          windowStart = existingLimit.windowStart.toMillis();
        } else {
          windowStart = existingLimit.windowStart;
        }
      }

      // Check if we're still in the same 30-minute window
      if (now - windowStart < thirtyMinutesMs) {
        // Same window - check count
        orderRateLimit = {
          count: existingLimit.count || 0,
          windowStart: windowStart,
        };

        if (orderRateLimit.count >= 10) {
          const remainingMs = thirtyMinutesMs - (now - windowStart);
          const remainingMins = Math.ceil(remainingMs / 60000);
          const plural = remainingMins > 1 ? "s" : "";
          throw new Error(
              "Rate limit exceeded. Max 10 orders per 30 minutes. " +
              `Try again in ${remainingMins} minute${plural}.`,
          );
        }
      } else {
        // Window expired - reset
        orderRateLimit = {
          count: 0,
          windowStart: now,
        };
      }
    }

    // Increment the order count for this window
    const newRateLimit = {
      count: orderRateLimit.count + 1,
      windowStart: orderRateLimit.windowStart || now,
      lastOrderAt: admin.firestore.FieldValue.serverTimestamp(),
    };

    // Update user document with new rate limit data
    await userRef.set({
      orderRateLimit: newRateLimit,
    }, {merge: true});

    console.log(`Rate limit: ${newRateLimit.count}/10 in current window`);
    // ============================================
    // END RATE LIMITING
    // ============================================

    if (!payload.documents ||
        !Array.isArray(payload.documents) ||
        payload.documents.length === 0) {
      throw new Error("Documents array is required");
    }

    // Get shop-specific settings for pricing
    // Use shopId from payload, or default to "GBLOCK" if not provided
    const targetShopId = payload.shopId || "GBLOCK";
    const shopSettingsDoc = await db
        .collection("shop_settings")
        .doc(targetShopId)
        .get();

    if (!shopSettingsDoc.exists) {
      throw new Error(`Shop settings not found for ${targetShopId}`);
    }

    const shopSettings = shopSettingsDoc.data();

    if (!shopSettings.isShopOpen) {
      const shopName = targetShopId === "GBLOCK" ? "GBlock" : "Cos";
      throw new Error(`${shopName} shop is currently closed`);
    }

    const calculatedPrice = calculateOrderPrice(
        payload.documents,
        shopSettings,
    );

    const priceDiff = Math.abs(calculatedPrice - payload.totalAmount);
    if (payload.totalAmount && priceDiff > 0.01) {
      throw new Error(
          "Price mismatch. Expected: " + calculatedPrice +
          ", Got: " + payload.totalAmount,
      );
    }

    const orderId = generateOrderId();

    // Build individualDocuments array with simplified structure
    const individualDocuments = payload.documents.map((doc) => {
      return {
        fileName: doc.fileName,
        fileType: doc.fileType,
        fileUrl: doc.fileUrl || "",
        printSettings: {
          customBWPages: doc.printSettings.customBWPages || "",
          customColorPages: doc.printSettings.customColorPages || "",
          copies: doc.printSettings.copies || 1,
          printOnBothSides: doc.printSettings.printOnBothSides === "true" ?
            "true" : "false",
          orientation: doc.printSettings.orientation || "PORTRAIT",
        },
      };
    });

    const orderData = {
      orderId: orderId,
      customerId: userId,
      customerPhone: payload.customerPhone || "",
      shopId: payload.shopId || "",
      fileType: payload.documents[0].fileType,
      individualDocuments: individualDocuments,
      documentCount: payload.documents.length,
      paymentStatus: "UNPAID",
      paymentAmount: calculatedPrice,
      orderStatus: "SUBMITTED",
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    };

    console.log("Order data to be saved:", JSON.stringify(orderData));

    await db.collection("print_orders").doc(orderId).set(orderData);

    console.log("Order created successfully:", orderId);

    return {
      success: true,
      orderId: orderId,
      amount: calculatedPrice,
    };
  } catch (error) {
    console.error("Error creating order:", error.message);
    console.error("Error stack:", error.stack);
    throw error;
  }
});

// ============================================
// CLOUD FUNCTION 2: INITIATE PAYMENT
// ============================================

exports.initiatePayment = onCall(
    {secrets: [razorpayKeyId, razorpayKeySecret]},
    async (request) => {
      try {
        console.log("=== INITIATE PAYMENT CALLED ===");

        // Authentication check
        if (!request.auth) {
          throw new Error("Unauthenticated");
        }

        // Create Razorpay instance with secrets (available at runtime)
        const razorpay = new Razorpay({
          key_id: razorpayKeyId.value(),
          key_secret: razorpayKeySecret.value(),
        });

        const payload = request.data.data || request.data;

        console.log("customerId:", payload.customerId);
        console.log("orderId:", payload.orderId);

        const {orderId} = payload;
        const customerId = request.auth.uid;

        // Verify customerId matches authenticated user (if provided)
        if (payload.customerId && payload.customerId !== customerId) {
          throw new Error("Unauthorized: customerId mismatch");
        }

        if (!orderId) {
          throw new Error("Order ID is required");
        }

        const orderDoc = await db.collection("print_orders").doc(orderId).get();

        if (!orderDoc.exists) {
          throw new Error("Order not found");
        }

        const order = orderDoc.data();

        if (order.customerId !== customerId) {
          throw new Error("Unauthorized access to order");
        }

        if (order.paymentStatus === "PAID") {
          throw new Error("Order is already paid");
        }

        // Create Razorpay order
        const amountInPaise = Math.round(order.paymentAmount * 100);

        const razorpayOrderOptions = {
          amount: amountInPaise,
          currency: "INR",
          receipt: orderId,
          notes: {
            orderId: orderId,
            customerId: customerId,
          },
        };

        console.log("Creating Razorpay order:", razorpayOrderOptions);

        const razorpayOrder = await razorpay.orders.create(
            razorpayOrderOptions,
        );

        console.log("Razorpay order created:", razorpayOrder.id);

        // Store payment details
        await db.collection("payment_transactions").doc(razorpayOrder.id).set({
          razorpayOrderId: razorpayOrder.id,
          orderId: orderId,
          customerId: customerId,
          amount: order.paymentAmount,
          amountInPaise: amountInPaise,
          status: "CREATED",
          createdAt: admin.firestore.FieldValue.serverTimestamp(),
        });

        // Update order
        await db.collection("print_orders").doc(orderId).update({
          razorpayOrderId: razorpayOrder.id,
          updatedAt: admin.firestore.FieldValue.serverTimestamp(),
        });

        console.log("Payment initiated:", razorpayOrder.id);

        return {
          success: true,
          razorpayOrderId: razorpayOrder.id,
          amount: order.paymentAmount,
          amountInPaise: amountInPaise,
          currency: "INR",
          keyId: razorpayKeyId.value(),
        };
      } catch (error) {
        console.error("Error initiating payment:", error.message);
        console.error("Error stack:", error.stack);
        throw error;
      }
    });

// ============================================
// CLOUD FUNCTION 3: VERIFY PAYMENT
// ============================================

exports.verifyPayment = onCall(
    {secrets: [razorpayKeyId, razorpayKeySecret]},
    async (request) => {
      try {
        console.log("=== VERIFY PAYMENT CALLED ===");

        // Authentication check
        if (!request.auth) {
          throw new Error("Unauthenticated");
        }

        // Create Razorpay instance with secrets (available at runtime)
        const razorpay = new Razorpay({
          key_id: razorpayKeyId.value(),
          key_secret: razorpayKeySecret.value(),
        });

        const payload = request.data.data || request.data;

        const customerId = request.auth.uid;

        console.log("customerId:", customerId);
        console.log("razorpayOrderId:", payload.razorpayOrderId);
        console.log("razorpayPaymentId:", payload.razorpayPaymentId);

        // Verify customerId matches authenticated user (if provided)
        if (payload.customerId && payload.customerId !== customerId) {
          throw new Error("Unauthorized: customerId mismatch");
        }

        const {
          razorpayOrderId,
          razorpayPaymentId,
          razorpaySignature,
          orderId,
        } = payload;

        if (!razorpayOrderId || !razorpayPaymentId || !orderId) {
          throw new Error("Missing required payment parameters");
        }

        // Get transaction details
        const txnDoc = await db
            .collection("payment_transactions")
            .doc(razorpayOrderId)
            .get();

        if (!txnDoc.exists) {
          throw new Error("Transaction not found");
        }

        const txnData = txnDoc.data();

        if (txnData.customerId !== customerId) {
          throw new Error("Unauthorized access to transaction");
        }

        // Verify signature using secret
        const generatedSignature = crypto
            .createHmac("sha256", razorpayKeySecret.value())
            .update(razorpayOrderId + "|" + razorpayPaymentId)
            .digest("hex");

        console.log("Generated signature:", generatedSignature);
        console.log("Received signature:", razorpaySignature);

        // SECURITY FIX: Signature is REQUIRED - reject if missing or invalid
        if (!razorpaySignature || razorpaySignature.trim() === "") {
          console.error("Payment signature is missing");

          await db.collection("payment_transactions")
              .doc(razorpayOrderId)
              .update({
                status: "FAILED",
                failureReason: "Missing signature",
                updatedAt: admin.firestore.FieldValue.serverTimestamp(),
              });

          throw new Error("Payment verification failed - Signature required");
        }

        if (generatedSignature !== razorpaySignature) {
          console.error("Signature verification failed");

          await db.collection("payment_transactions")
              .doc(razorpayOrderId)
              .update({
                status: "FAILED",
                failureReason: "Invalid signature",
                updatedAt: admin.firestore.FieldValue.serverTimestamp(),
              });

          throw new Error("Payment verification failed - Invalid signature");
        }

        console.log("Signature verified successfully");

        // Fetch payment details from Razorpay
        const payment = await razorpay.payments.fetch(razorpayPaymentId);

        console.log("Payment status:", payment.status);
        console.log("Payment amount:", payment.amount);

        // Verify amount
        const expectedAmount = txnData.amountInPaise;
        if (payment.amount !== expectedAmount) {
          throw new Error("Amount mismatch");
        }

        // Update transaction
        await db.collection("payment_transactions")
            .doc(razorpayOrderId)
            .update({
              razorpayPaymentId: razorpayPaymentId,
              status: payment.status === "captured" ? "SUCCESS" : "PENDING",
              paymentMethod: payment.method,
              verified: true,
              verifiedAt: admin.firestore.FieldValue.serverTimestamp(),
              updatedAt: admin.firestore.FieldValue.serverTimestamp(),
            });

        // Update order
        if (payment.status === "captured") {
          await db.collection("print_orders").doc(orderId).update({
            paymentStatus: "PAID",
            razorpayPaymentId: razorpayPaymentId,
            updatedAt: admin.firestore.FieldValue.serverTimestamp(),
          });

          console.log("Payment verified and order updated:", orderId);

          return {
            success: true,
            status: "PAID",
            message: "Payment verified successfully",
          };
        } else {
          return {
            success: false,
            status: payment.status.toUpperCase(),
            message: "Payment not captured yet",
          };
        }
      } catch (error) {
        console.error("Error verifying payment:", error.message);
        console.error("Error stack:", error.stack);
        throw error;
      }
    });

// ============================================
// CLOUD FUNCTION 4: CHECK ORDER STATUS
// ============================================

exports.checkOrderStatus = onCall(async (request) => {
  try {
    // Authentication check
    if (!request.auth) {
      throw new Error("Unauthenticated");
    }

    const payload = request.data.data || request.data;
    const {orderId} = payload;
    const customerId = request.auth.uid;

    // Verify customerId matches authenticated user (if provided)
    if (payload.customerId && payload.customerId !== customerId) {
      throw new Error("Unauthorized: customerId mismatch");
    }

    if (!orderId) {
      throw new Error("Order ID is required");
    }

    const orderDoc = await db.collection("print_orders").doc(orderId).get();

    if (!orderDoc.exists) {
      throw new Error("Order not found");
    }

    const order = orderDoc.data();

    if (order.customerId !== customerId) {
      throw new Error("Unauthorized access to order");
    }

    return {
      success: true,
      order: {
        orderId: order.orderId,
        paymentStatus: order.paymentStatus,
        orderStatus: order.orderStatus,
        amount: order.paymentAmount,
      },
    };
  } catch (error) {
    console.error("Error checking order status:", error);
    throw error;
  }
});

// ============================================
// CLOUD FUNCTION 5: MARK ORDER AS SUBMITTED (Website upload complete)
// ============================================

exports.markOrderAsSubmitted = onCall(async (request) => {
  try {
    console.log("=== MARK ORDER AS SUBMITTED ===");

    // Authentication check
    if (!request.auth) {
      throw new Error("Unauthenticated");
    }

    const {orderId} = request.data;

    if (!orderId) {
      throw new Error("Order ID is required");
    }

    const orderRef = db.collection("print_orders").doc(orderId);
    const orderDoc = await orderRef.get();

    if (!orderDoc.exists) {
      throw new Error("Order not found");
    }

    const orderData = orderDoc.data();

    // Only allow status change from PENDING to SUBMITTED
    if (orderData.orderStatus !== "PENDING") {
      throw new Error(
          `Order status is ${orderData.orderStatus}, expected PENDING`,
      );
    }

    // Update status to SUBMITTED
    await orderRef.update({
      orderStatus: "SUBMITTED",
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    });

    console.log("Order marked as submitted:", orderId);

    return {success: true};
  } catch (error) {
    console.error("Error marking order as submitted:", error.message);
    throw error;
  }
});

// ============================================
// CLOUD FUNCTION 6: SEND FCM NOTIFICATION ON ORDER STATUS CHANGE
// ============================================

exports.onOrderStatusChanged = onDocumentUpdated(
    "print_orders/{orderId}",
    async (event) => {
      try {
        const beforeData = event.data.before.data();
        const afterData = event.data.after.data();

        // Check if orderStatus changed to PRINTED
        if (
          beforeData.orderStatus !== "PRINTED" &&
          afterData.orderStatus === "PRINTED"
        ) {
          console.log("Order status changed to PRINTED:", event.params.orderId);
          console.log("Customer ID:", afterData.customerId);

          const customerId = afterData.customerId;
          const orderId = afterData.orderId || event.params.orderId;

          if (!customerId) {
            console.error("Customer ID not found in order");
            return;
          }

          // Get user's FCM token
          const userDoc = await db.collection("users")
              .doc(customerId)
              .get();

          if (!userDoc.exists) {
            console.error("User document not found:", customerId);
            return;
          }

          const latestFcmToken = userDoc.data().latestFcmToken;

          if (!latestFcmToken) {
            console.warn("No FCM token found for user:", customerId);
            return;
          }

          console.log("Sending notification to token:", latestFcmToken);

          // Prepare notification message
          const message = {
            notification: {
              title: "Order Printed! 🎉",
              body: `Your order ${orderId} has been printed ` +
                  `and is ready for pickup.`,
            },
            data: {
              orderId: orderId,
              orderStatus: "PRINTED",
              type: "order_status_update",
            },
            token: latestFcmToken,
            android: {
              priority: "high",
              notification: {
                sound: "default",
                channelId: "order_notifications",
              },
            },
          };

          // Send notification
          const response = await admin.messaging().send(message);
          console.log("Successfully sent notification:", response);

          // Log notification sent
          await db.collection("notifications").add({
            userId: customerId,
            orderId: orderId,
            type: "order_printed",
            title: message.notification.title,
            body: message.notification.body,
            sentAt: admin.firestore.FieldValue.serverTimestamp(),
            fcmToken: latestFcmToken,
          });
        }
      } catch (error) {
        console.error("Error in onOrderStatusChanged:", error);
        console.error("Error stack:", error.stack);
        // Don't throw - we don't want to fail the order update
      }
    },
);
