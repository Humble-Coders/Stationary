const {onCall} = require("firebase-functions/v2/https");
const {setGlobalOptions} = require("firebase-functions/v2");
const admin = require("firebase-admin");
const Razorpay = require("razorpay");
const crypto = require("crypto");

// Set global options
setGlobalOptions({
  region: "us-central1",
  memory: "256MiB",
});

admin.initializeApp();
const db = admin.firestore();

// ============================================
// RAZORPAY CONFIGURATION
// ============================================

const RAZORPAY_CONFIG = {
  KEY_ID: "rzp_test_RzWE1wWsadiMei", // Replace with actual
  KEY_SECRET: "X17sE0R7Z7vfnziJBjtMkmm1", // Replace with actual
};

const razorpay = new Razorpay({
  key_id: RAZORPAY_CONFIG.KEY_ID,
  key_secret: RAZORPAY_CONFIG.KEY_SECRET,
});

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
      if (trimmed.includes("-")) {
        const range = trimmed.split("-");
        if (range.length === 2) {
          const start = Math.max(1, parseInt(range[0].trim(), 10));
          const end = Math.max(1, parseInt(range[1].trim(), 10));
          if (start <= end) {
            for (let i = start; i <= end; i++) {
              pages.add(i);
            }
          }
        }
      } else {
        const page = parseInt(trimmed, 10);
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
    const copies = settings.copies || 1;

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

// ============================================
// CLOUD FUNCTION 1: CREATE ORDER
// ============================================

exports.createOrder = onCall(async (request) => {
  try {
    console.log("=== CREATE ORDER CALLED ===");

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

    const userId = payload.customerId;

    if (!userId) {
      console.error("customerId is missing");
      throw new Error("Customer ID is required");
    }

    console.log("Processing order for user:", userId);

    if (!payload.documents ||
        !Array.isArray(payload.documents) ||
        payload.documents.length === 0) {
      throw new Error("Documents array is required");
    }

    const shopSettingsDoc = await db
        .collection("shop_settings")
        .doc("default")
        .get();

    if (!shopSettingsDoc.exists) {
      throw new Error("Shop settings not found");
    }

    const shopSettings = shopSettingsDoc.data();

    if (!shopSettings.shopOpen) {
      throw new Error("Shop is currently closed");
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
          printOnBothSides: doc.printSettings.printOnBothSides || false,
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

exports.initiatePayment = onCall(async (request) => {
  try {
    console.log("=== INITIATE PAYMENT CALLED ===");

    const payload = request.data.data || request.data;

    console.log("customerId:", payload.customerId);
    console.log("orderId:", payload.orderId);

    const {orderId, customerId} = payload;

    if (!orderId || !customerId) {
      throw new Error("Order ID and Customer ID are required");
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
      keyId: RAZORPAY_CONFIG.KEY_ID,
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

exports.verifyPayment = onCall(async (request) => {
  try {
    console.log("=== VERIFY PAYMENT CALLED ===");

    const payload = request.data.data || request.data;

    console.log("customerId:", payload.customerId);
    console.log("razorpayOrderId:", payload.razorpayOrderId);
    console.log("razorpayPaymentId:", payload.razorpayPaymentId);

    const {
      razorpayOrderId,
      razorpayPaymentId,
      razorpaySignature,
      customerId,
      orderId,
    } = payload;

    if (!razorpayOrderId || !razorpayPaymentId || !customerId || !orderId) {
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

    // Verify signature
    const generatedSignature = crypto
        .createHmac("sha256", RAZORPAY_CONFIG.KEY_SECRET)
        .update(razorpayOrderId + "|" + razorpayPaymentId)
        .digest("hex");

    console.log("Generated signature:", generatedSignature);
    console.log("Received signature:", razorpaySignature);

    if (razorpaySignature && generatedSignature !== razorpaySignature) {
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
    const payload = request.data.data || request.data;
    const {orderId, customerId} = payload;

    if (!orderId || !customerId) {
      throw new Error("Order ID and Customer ID are required");
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
