const {onCall, onRequest} = require("firebase-functions/v2/https");
const {onDocumentUpdated} = require("firebase-functions/v2/firestore");
const {setGlobalOptions} = require("firebase-functions/v2");
const {defineSecret} = require("firebase-functions/params");
const admin = require("firebase-admin");
const Razorpay = require("razorpay");
const crypto = require("crypto");
const axios = require("axios");
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

// WhatsApp Cloud API access token (stored in Firebase Secret Manager)
// Set this via: firebase functions:secrets:set WHATSAPP_ACCESS_TOKEN
const whatsappAccessToken = defineSecret("WHATSAPP_ACCESS_TOKEN");

// ============================================
// WHATSAPP CONFIGURATION
// ============================================

const WHATSAPP_VERIFY_TOKEN = "printq_verify_123";
const WHATSAPP_GROUPING_WINDOW_MS = 5 * 60 * 1000; // 5 minutes

// WhatsApp order limits (match website limits)
const WA_MAX_FILE_SIZE = 50 * 1024 * 1024; // 50 MB
const WA_MAX_TOTAL_ORDER_SIZE = 150 * 1024 * 1024; // 150 MB
const WA_MAX_DOCUMENTS = 10; // PDF, docx, xlsx, pptx, etc.
const WA_MAX_IMAGES = 50;

// MIME to extension map for generating filenames when WhatsApp omits them
const MIME_TO_EXT = {
  "application/pdf": ".pdf",
  "image/jpeg": ".jpg",
  "image/png": ".png",
  "image/webp": ".webp",
  "image/gif": ".gif",
  "image/bmp": ".bmp",
  "image/heic": ".heic",
  "image/heif": ".heif",
  "image/svg+xml": ".svg",
  "image/tiff": ".tiff",
  "image/x-tiff": ".tiff",
  "image/ico": ".ico",
  "application/vnd.openxmlformats-officedocument.wordprocessingml.document":
    ".docx",
  "application/vnd.openxmlformats-officedocument.presentationml.presentation":
    ".pptx",
  "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet": ".xlsx",
  "application/msword": ".doc",
  "application/vnd.ms-powerpoint": ".ppt",
  "application/vnd.ms-excel": ".xls",
  "text/plain": ".txt",
  "application/rtf": ".rtf",
  "text/rtf": ".rtf",
};

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

// ============================================
// WHATSAPP HELPER FUNCTIONS
// ============================================

/**
 * Generate unique WhatsApp order ID with WA prefix
 * @return {string} Generated order ID
 */
function generateWhatsAppOrderId() {
  return "WA" + Date.now() +
    Math.random().toString(36).substr(2, 9).toUpperCase();
}

/**
 * Download media file from WhatsApp Cloud API
 * Step 1: Get the media URL using the media ID
 * Step 2: Download the actual file binary from that URL
 *
 * @param {string} mediaId - WhatsApp media ID from the webhook payload
 * @param {string} accessToken - WhatsApp Cloud API access token
 * @return {Object} { buffer, mimeType }
 */
async function downloadWhatsAppMedia(mediaId, accessToken) {
  // Step 1: Get the media download URL from WhatsApp
  const mediaInfoResponse = await axios.get(
      `https://graph.facebook.com/v21.0/${mediaId}`,
      {
        headers: {Authorization: `Bearer ${accessToken}`},
      },
  );

  const mediaUrl = mediaInfoResponse.data.url;
  const mimeType = mediaInfoResponse.data.mime_type;

  console.log(`Media URL retrieved for ${mediaId}, mimeType: ${mimeType}`);

  // Step 2: Download the actual file binary
  const fileResponse = await axios.get(mediaUrl, {
    headers: {Authorization: `Bearer ${accessToken}`},
    responseType: "arraybuffer",
  });

  return {
    buffer: Buffer.from(fileResponse.data),
    mimeType: mimeType,
  };
}

/**
 * Upload file buffer to Firebase Storage and return a signed URL
 *
 * @param {Buffer} buffer - File binary data
 * @param {string} fileName - Original file name
 * @param {string} orderId - Order ID (used as folder name)
 * @param {string} mimeType - MIME type of the file
 * @return {string} Signed download URL (valid for 30 days)
 */
async function uploadToFirebaseStorage(buffer, fileName, orderId, mimeType) {
  const bucket = getStorage().bucket();
  const filePath = `whatsapp_uploads/${orderId}/${fileName}`;
  const file = bucket.file(filePath);

  // Upload the file buffer to Firebase Storage
  await file.save(buffer, {
    metadata: {
      contentType: mimeType,
    },
  });

  // Generate a signed URL valid for 30 days
  const [signedUrl] = await file.getSignedUrl({
    action: "read",
    expires: Date.now() + 30 * 24 * 60 * 60 * 1000,
  });

  return signedUrl;
}

/**
 * Extract PDF page count only. Thumbnail is generated client-side.
 *
 * @param {Buffer} buffer - PDF file buffer
 * @return {Promise<number>} Page count or 0 on failure
 */
async function extractPdfPageCount(buffer) {
  const pdfParse = require("pdf-parse");
  try {
    const pdfData = await pdfParse(buffer);
    return pdfData.numpages || 0;
  } catch (err) {
    console.error("PDF page count error:", err.message);
    return 0;
  }
}

/**
 * Find an existing pending order for this phone within the 5-minute
 * time window, or create a new one. Uses a per-phone cache doc and
 * transaction to prevent race conditions when multiple messages arrive
 * simultaneously.
 *
 * Time-window grouping logic (unchanged):
 * - If the SAME phone has a pending order within 5 minutes → reuse it.
 * - If no such order or >5 min have passed → create a new order.
 *
 * @param {string} phone - Sender phone number (e.g. "+919876543210")
 * @return {Promise<{orderId: string, isNew: boolean}>}
 */
async function findOrCreateWhatsAppOrder(phone) {
  const normalizedPhone = phone.replace(/\D/g, "") || "unknown";
  const cacheRef = db.collection("whatsapp_order_cache").doc(normalizedPhone);
  const now = new Date();
  const windowStart = new Date(
      now.getTime() - WHATSAPP_GROUPING_WINDOW_MS,
  );

  const result = await db.runTransaction(async (t) => {
    const cacheSnap = await t.get(cacheRef);

    if (cacheSnap.exists) {
      const data = cacheSnap.data();
      const orderId = data.orderId;
      const updatedAt = data.updatedAt;
      const cacheTime = (updatedAt && updatedAt.toDate) ?
        updatedAt.toDate() :
        new Date(updatedAt);
      if (cacheTime >= windowStart) {
        // Reuse: update cache to extend 5-min window
        t.update(cacheRef, {
          updatedAt: admin.firestore.FieldValue.serverTimestamp(),
        });
        console.log(`Reusing existing order: ${orderId} for ${phone}`);
        return {orderId, isNew: false};
      }
    }

    // No valid cache: create new order
    const orderId = generateWhatsAppOrderId();
    const orderRef = db.collection("whatsapp_uploads").doc(orderId);

    t.set(orderRef, {
      phone: phone,
      status: "pending",
      files: [],
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    });
    t.set(cacheRef, {
      orderId: orderId,
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    });

    console.log(`Created new WhatsApp order: ${orderId} for ${phone}`);
    return {orderId, isNew: true};
  });

  return result;
}

/**
 * Send a text message to a WhatsApp user.
 * Reply within the same conversation window = no extra cost.
 *
 * @param {string} phoneNumberId - From value.metadata.phone_number_id
 * @param {string} to - Recipient number without + (e.g. "919876543210")
 * @param {string} text - Message body
 * @param {string} accessToken - WhatsApp Cloud API access token
 */
async function sendWhatsAppTextMessage(phoneNumberId, to, text, accessToken) {
  await axios.post(
      `https://graph.facebook.com/v21.0/${phoneNumberId}/messages`,
      {
        messaging_product: "whatsapp",
        recipient_type: "individual",
        to: to.replace(/^\+/, ""),
        type: "text",
        text: {body: text},
      },
      {
        headers: {
          "Authorization": `Bearer ${accessToken}`,
          "Content-Type": "application/json",
        },
      },
  );
}

/**
 * Check if a MIME type is an image type.
 * @param {string} mimeType
 * @return {boolean}
 */
function isImageMimeType(mimeType) {
  return !!(mimeType && mimeType.startsWith("image/"));
}

/**
 * Send a WhatsApp limit error message and return.
 * @param {string} phoneNumberId
 * @param {string} to - Recipient without +
 * @param {string} orderId
 * @param {string} errorReason - Human-readable reason
 * @param {string} accessToken
 */
async function sendLimitErrorMessage(
    phoneNumberId, to, orderId, errorReason, accessToken,
) {
  const orderUrl = `https://printq.tech/?order=${orderId}`;
  const messageText =
    `❌ ${errorReason}\n\n` +
    `Please go to the link and confirm this order. ` +
    `You can add more files in your next order.\n\n` +
    `🔗 ${orderUrl}`;
  try {
    await sendWhatsAppTextMessage(
        phoneNumberId, to, messageText, accessToken,
    );
  } catch (err) {
    console.error("Failed to send limit error:", err.message);
  }
}

/**
 * Extract media info from WhatsApp message. Supports document and image types.
 * Matches website allowed types: PDF, images, docx, pptx, xlsx, etc.
 *
 * @param {Object} message - WhatsApp webhook message object
 * @return {Object|null} {mediaId, mimeType, fileName} or null if unsupported
 */
function extractMediaFromWhatsAppMessage(message) {
  if (message.type === "document" && message.document) {
    const d = message.document;
    const mimeType = d.mime_type || "application/octet-stream";
    const ext = MIME_TO_EXT[mimeType] || ".bin";
    return {
      mediaId: d.id,
      mimeType: mimeType,
      fileName: d.filename || "document_" + Date.now() + ext,
    };
  }
  if (message.type === "image" && message.image) {
    const img = message.image;
    const mimeType = img.mime_type || "image/jpeg";
    const ext = MIME_TO_EXT[mimeType] || ".jpg";
    return {
      mediaId: img.id,
      mimeType: mimeType,
      fileName: "image_" + Date.now() + ext,
    };
  }
  return null;
}

/**
 * Process a media message (document or image) from WhatsApp.
 * Downloads the file, uploads to Storage, and adds to Firestore order.
 *
 * @param {Object} message - WhatsApp message object
 * @param {Object} mediaInfo - {mediaId, mimeType, fileName} from extractMedia
 * @param {string} accessToken - WhatsApp Cloud API access token
 * @param {string} phoneNumberId - Phone number ID from webhook metadata
 */
async function processMediaMessage(
    message, mediaInfo, accessToken, phoneNumberId,
) {
  const phone = "+" + message.from;
  const {mediaId, mimeType, fileName} = mediaInfo;
  const isImage = isImageMimeType(mimeType);

  console.log("=== PROCESSING WHATSAPP MEDIA ===");
  console.log(`Phone: ${phone}`);
  console.log(`File: ${fileName} (${mimeType})`);
  console.log(`Media ID: ${mediaId}`);

  // Step 1: Find existing order or create new one
  const {orderId, isNew} = await findOrCreateWhatsAppOrder(phone);

  // Step 2: Download file from WhatsApp Cloud API
  const {buffer} = await downloadWhatsAppMedia(
      mediaId, accessToken,
  );
  const fileSize = buffer.length;
  console.log(`Downloaded: ${fileName} (${fileSize} bytes)`);

  // Step 3: Enforce limits before uploading

  // 3a: Single file size limit (50 MB)
  if (fileSize > WA_MAX_FILE_SIZE) {
    const sizeMB = (fileSize / (1024 * 1024)).toFixed(1);
    console.warn(`File too large: ${sizeMB} MB > 50 MB`);
    if (phoneNumberId) {
      await sendLimitErrorMessage(
          phoneNumberId, message.from, orderId,
          `File "${fileName}" is ${sizeMB} MB which exceeds ` +
          `the 50 MB limit. It was not added to your order.`,
          accessToken,
      );
    }
    return;
  }

  // 3b: For existing orders, check count + size limits
  if (!isNew) {
    const orderRef = db
        .collection("whatsapp_uploads").doc(orderId);
    const orderSnap = await orderRef.get();
    const existingFiles = (orderSnap.data().files) || [];

    // Count images vs documents
    let imgCount = 0;
    let docCount = 0;
    let totalSize = 0;
    for (const f of existingFiles) {
      totalSize += f.fileSize || 0;
      if (isImageMimeType(f.mimeType)) {
        imgCount++;
      } else {
        docCount++;
      }
    }

    // Check type-specific count limit
    if (isImage && imgCount >= WA_MAX_IMAGES) {
      console.warn(
          `Image limit reached: ${imgCount}/${WA_MAX_IMAGES}`,
      );
      if (phoneNumberId) {
        await sendLimitErrorMessage(
            phoneNumberId, message.from, orderId,
            `Image was not added — this order already has ` +
            `${imgCount} images (max ${WA_MAX_IMAGES}).`,
            accessToken,
        );
      }
      return;
    }
    if (!isImage && docCount >= WA_MAX_DOCUMENTS) {
      console.warn(
          `Document limit: ${docCount}/${WA_MAX_DOCUMENTS}`,
      );
      if (phoneNumberId) {
        await sendLimitErrorMessage(
            phoneNumberId, message.from, orderId,
            `Document was not added — this order already ` +
            `has ${docCount} documents (max ` +
            `${WA_MAX_DOCUMENTS}).`,
            accessToken,
        );
      }
      return;
    }

    // Check total order size limit (150 MB)
    if (totalSize + fileSize > WA_MAX_TOTAL_ORDER_SIZE) {
      const curMB = (totalSize / (1024 * 1024)).toFixed(1);
      const maxMB = (
        WA_MAX_TOTAL_ORDER_SIZE / (1024 * 1024)
      ).toFixed(0);
      console.warn(
          `Order size limit: ${curMB} MB + file > ${maxMB} MB`,
      );
      if (phoneNumberId) {
        await sendLimitErrorMessage(
            phoneNumberId, message.from, orderId,
            `File was not added — total order size would ` +
            `exceed ${maxMB} MB (currently ${curMB} MB).`,
            accessToken,
        );
      }
      return;
    }
  }

  // Step 4: Upload file to Firebase Storage
  const fileUrl = await uploadToFirebaseStorage(
      buffer, fileName, orderId, mimeType,
  );
  console.log(`Uploaded to Storage: ${fileName}`);

  // Step 4b: For PDFs, extract page count only.
  // Thumbnail is generated client-side via PDF.js.
  let pageCount = null;
  const isPdf = mimeType === "application/pdf" ||
    (fileName && fileName.toLowerCase().endsWith(".pdf"));
  if (isPdf) {
    pageCount = await extractPdfPageCount(buffer);
    console.log("PDF page count:", pageCount);
  }

  // Step 5: Add file to order's files array in Firestore
  const fileObj = {
    fileName: fileName,
    fileUrl: fileUrl,
    mimeType: mimeType,
    fileSize: fileSize,
    uploadedAt: admin.firestore.Timestamp.now(),
  };
  if (pageCount !== null && pageCount > 0) {
    fileObj.pageCount = pageCount;
  }

  const orderRef = db
      .collection("whatsapp_uploads").doc(orderId);
  await orderRef.update({
    files: admin.firestore.FieldValue.arrayUnion(fileObj),
    updatedAt: admin.firestore.FieldValue.serverTimestamp(),
  });

  const orderSnap = await orderRef.get();
  const fileCount = (orderSnap.data().files || []).length;

  console.log(
      `File "${fileName}" added to order ` +
      `${orderId} (file ${fileCount})`,
  );

  // Step 6: Send WhatsApp confirmation
  if (phoneNumberId) {
    const orderUrl =
      `https://printq.tech/?order=${orderId}`;
    let messageText;
    if (isNew) {
      messageText =
        `✅ Your document has been received!\n\n` +
        `🔗 Order link: ${orderUrl}\n\n` +
        `Please confirm your order on the website. ` +
        `You can add more documents within the next ` +
        `5 minutes to the same order. After 5 minutes` +
        `, the order will expire and you'll need to ` +
        `create a new one.`;
    } else {
      messageText =
        `✅ Document ${fileCount} uploaded, ` +
        `click on same link above.\n\n` +
        `🔗 ${orderUrl}`;
    }

    try {
      await sendWhatsAppTextMessage(
          phoneNumberId,
          message.from,
          messageText,
          accessToken,
      );
      console.log("Confirmation sent to:", message.from);
    } catch (err) {
      console.error(
          "Failed to send WhatsApp message:", err.message,
      );
    }
  }

  console.log("=== MEDIA PROCESSING COMPLETE ===");
}

// ============================================
// WHATSAPP WEBHOOK ENDPOINT
// ============================================

/**
 * WhatsApp Webhook Cloud Function (HTTP endpoint)
 *
 * GET  → Webhook verification (required by Meta during setup)
 * POST → Incoming message handler (processes document messages)
 *
 * Deployed URL will be:
 *   https://us-central1-<project-id>.cloudfunctions.net/whatsappWebhook
 */
exports.whatsappWebhook = onRequest(
    {secrets: [whatsappAccessToken]},
    async (req, res) => {
      // ---- GET: Webhook Verification (Meta handshake) ----
      if (req.method === "GET") {
        const mode = req.query["hub.mode"];
        const token = req.query["hub.verify_token"];
        const challenge = req.query["hub.challenge"];

        if (mode === "subscribe" && token === WHATSAPP_VERIFY_TOKEN) {
          console.log("WhatsApp webhook verified successfully");
          res.status(200).send(challenge);
        } else {
          console.warn("WhatsApp webhook verification failed");
          res.sendStatus(403);
        }
        return;
      }

      // ---- POST: Incoming WhatsApp Message ----
      if (req.method === "POST") {
        const body = req.body;

        // Validate this is a WhatsApp Business Account event
        if (!body || body.object !== "whatsapp_business_account") {
          console.log("Not a WhatsApp Business event, ignoring");
          res.sendStatus(200);
          return;
        }

        try {
          // Process each entry in the webhook payload
          // WhatsApp payload structure:
          //   body.entry[].changes[].value.messages[]
          for (const entry of body.entry || []) {
            for (const change of entry.changes || []) {
              // Only process "messages" field changes
              if (change.field !== "messages") continue;

              const value = change.value;
              if (!value || !value.messages) continue;

              const phoneNumberId = value.metadata &&
                value.metadata.phone_number_id;

              for (const message of value.messages) {
                const mediaInfo = extractMediaFromWhatsAppMessage(message);
                if (!mediaInfo) {
                  console.log(
                      "Skipping unsupported message type:",
                      message.type || "unknown",
                  );
                  continue;
                }

                await processMediaMessage(
                    message,
                    mediaInfo,
                    whatsappAccessToken.value(),
                    phoneNumberId,
                );
              }
            }
          }
        } catch (error) {
          // Log error but still respond 200 to prevent WhatsApp retries
          console.error("Error processing WhatsApp webhook:", error.message);
          console.error("Error stack:", error.stack);
        }

        // Always respond 200 to acknowledge receipt
        // WhatsApp will retry the webhook if it doesn't get a 200
        res.sendStatus(200);
        return;
      }

      // Any other HTTP method is not supported
      res.sendStatus(405);
    },
);


// ============================================
// WHATSAPP ORDER SUBMISSION (from website review page)
// ============================================

/**
 * Submit a WhatsApp order after user reviews and configures print settings
 * on the website. Updates the order in whatsapp_uploads with print settings
 * and sets status to "submitted".
 *
 * Called from: printq.tech/?order=WA...
 */
exports.submitWhatsAppOrder = onCall(async (request) => {
  try {
    console.log("=== SUBMIT WHATSAPP ORDER ===");

    const payload = request.data.data || request.data;
    const {orderId, files, shopId, customerName} = payload;

    if (!orderId) {
      throw new Error("Order ID is required");
    }

    if (!shopId || (shopId !== "GBLOCK" && shopId !== "COS")) {
      throw new Error("Valid shop ID is required");
    }

    if (!files || !Array.isArray(files) || files.length === 0) {
      throw new Error("At least one file is required");
    }

    // Fetch the order
    const orderRef = db.collection("whatsapp_uploads").doc(orderId);
    const orderDoc = await orderRef.get();

    if (!orderDoc.exists) {
      throw new Error("Order not found");
    }

    const orderData = orderDoc.data();

    if (orderData.status !== "pending") {
      throw new Error(`Order already ${orderData.status}`);
    }

    // Validate shop is open
    const shopDoc = await db
        .collection("shop_settings")
        .doc(shopId)
        .get();

    if (!shopDoc.exists) {
      throw new Error("Shop not found");
    }

    if (!shopDoc.data().isShopOpen) {
      throw new Error("Shop is currently closed");
    }

    // Build sanitized files array with print settings
    const sanitizedFiles = files.map((f) => ({
      fileName: f.fileName || "unnamed",
      fileUrl: f.fileUrl || "",
      mimeType: f.mimeType || "application/octet-stream",
      printSettings: f.printSettings || {},
      uploadedAt: f.uploadedAt || new Date(),
    }));

    // Update order: add print settings, shop, customer name, and set status
    const updateData = {
      files: sanitizedFiles,
      shopId: shopId,
      status: "submitted",
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    };
    if (customerName && typeof customerName === "string") {
      const trimmed = customerName.trim();
      if (trimmed.length > 0 && trimmed.length <= 100) {
        updateData.customerName = trimmed;
      }
    }
    await orderRef.update(updateData);

    // Clear order cache so next batch of messages creates a new order
    const phone = orderData.phone;
    if (phone) {
      const normalizedPhone = String(phone).replace(/\D/g, "");
      if (normalizedPhone) {
        await db.collection("whatsapp_order_cache")
            .doc(normalizedPhone)
            .delete();
        console.log("Cleared whatsapp_order_cache for phone:", normalizedPhone);
      }
    }

    console.log("WhatsApp order submitted:", orderId, "Shop:", shopId);

    return {success: true, orderId};
  } catch (error) {
    console.error("Error submitting WhatsApp order:", error.message);
    throw error;
  }
});


exports.createWebsitePrintOrder = onCall({
  enforceAppCheck: true,
}, async (request) => {
  try {
    console.log("=== WEBSITE CREATE PRINT ORDER ===");

    // Verify App Check token is present
    if (request.app == undefined) {
      throw new Error("App Check verification failed");
    }

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

exports.markOrderAsSubmitted = onCall({
  enforceAppCheck: true,
}, async (request) => {
  try {
    console.log("=== MARK ORDER AS SUBMITTED ===");

    // Verify App Check token is present
    if (request.app == undefined) {
      throw new Error("App Check verification failed");
    }

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
