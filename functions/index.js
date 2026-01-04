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
 * Calculate order price (server-side validation)
 * @param {Array} documents - Array of document objects
 * @param {Object} shopSettings - Shop settings object
 * @return {number} Calculated price
 */
function calculateOrderPrice(documents, shopSettings) {
  let totalPrice = 0;

  console.log("Shop settings received:", shopSettings);
  console.log("Shop settings keys:", Object.keys(shopSettings || {}));

  if (!shopSettings || !shopSettings.pricing) {
    console.error("shopSettings.pricing is undefined");
    throw new Error("Shop pricing configuration not found");
  }

  const pricing = shopSettings.pricing;
  console.log("Pricing object:", pricing);

  documents.forEach((doc) => {
    const settings = doc.printSettings;
    const pageCount = doc.pageCount;
    const copies = settings.copies || 1;

    let bwPages = 0;
    let colorPages = 0;

    if (settings.pagesToPrint === "ALL") {
      if (settings.colorMode === "BW") {
        bwPages = pageCount;
      } else if (settings.colorMode === "COLOR") {
        colorPages = pageCount;
      } else if (settings.colorMode === "MIXED") {
        bwPages = settings.customBWPages ? settings.customBWPages.length : 0;
        colorPages = settings.customColorPages ?
            settings.customColorPages.length : 0;
      }
    } else if (settings.pagesToPrint === "CUSTOM") {
      const customPages = settings.customPages ?
          settings.customPages.length : 0;
      if (settings.colorMode === "BW") {
        bwPages = customPages;
      } else if (settings.colorMode === "COLOR") {
        colorPages = customPages;
      } else if (settings.colorMode === "MIXED") {
        bwPages = settings.customBWPages ? settings.customBWPages.length : 0;
        colorPages = settings.customColorPages ?
            settings.customColorPages.length : 0;
      }
    }

    const bwPrice = pricing.bw || 0;
    const colorPrice = pricing.color || 0;

    console.log("Document pricing - BW:", bwPrice, "Color:", colorPrice);
    console.log(
        "Pages - BW:", bwPages,
        "Color:", colorPages,
        "Copies:", copies,
    );

    const docPrice = (bwPages * bwPrice + colorPages * colorPrice) * copies;
    totalPrice += docPrice;
  });

  console.log("Total calculated price:", totalPrice);
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

    const orderData = {
      orderId: orderId,
      customerId: userId,
      customerPhone: payload.customerPhone || "",
      documentName: payload.documents.map((d) => d.fileName),
      documentUrl: payload.documents.map((d) => d.url),
      documentSize: payload.documents.reduce((sum, d) => sum + d.fileSize, 0),
      fileType: payload.documents[0].fileType,
      pageCount: payload.documents.reduce((sum, d) => sum + d.pageCount, 0),
      printSettings: payload.documents.map((d) => d.printSettings),
      individualDocuments: payload.documents,
      documentCount: payload.documents.length,
      paymentStatus: "UNPAID",
      paymentAmount: calculatedPrice,
      orderStatus: "SUBMITTED",
      hasSettings: true,
      isPaid: false,
      canAutoPrint: false,
      queuePriority: 0,
      inQueue: false,
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    };

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

    if (order.isPaid || order.paymentStatus === "PAID") {
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
        isPaid: true,
        canAutoPrint: true,
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
        isPaid: order.isPaid,
        amount: order.paymentAmount,
      },
    };
  } catch (error) {
    console.error("Error checking order status:", error);
    throw error;
  }
});
