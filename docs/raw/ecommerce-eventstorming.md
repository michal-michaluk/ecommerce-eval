# E-Commerce Event Storming — Meeting Summary

**Date:** June 15, 2026
**Source:** `ecommerce-eventstorming.mov`
**Duration:** ~32 minutes
**Domain:** E-commerce / Product Catalog & Order Fulfillment

---

## Overview

This session covers a domain walkthrough of an e-commerce platform using Event Storming methodology. The speaker walks through the business flows from product creation through checkout to fulfillment, using the diagram as the primary reference.

---

## 1. Offer Management

**[(00:00:01)](file:///Users/michal/workspace/stem/bottega/ecommerce-eventstorming.mov#t=00:00:01)** — Start of product catalog management overview

### Product Creation & Description

**[(00:00:13)](file:///Users/michal/workspace/stem/bottega/ecommerce-eventstorming.mov#t=00:00:13)** — The process begins with a **content manager** who creates a **new product** or a **new version of a product**. The initial step involves filling basic information: the **product title** and **IDs** for reference later. The title is not critical at this stage and can be changed at any time.

**[(00:01:06)](file:///Users/michal/workspace/stem/bottega/ecommerce-eventstorming.mov#t=00:01:06)** — After creation, an almost blank **draft product description** is available. The content manager can immediately add descriptions, titles, and other information. Every edit triggers a **description updated** event, but this update remains **private** within the catalog management domain until explicitly published.

### Marketing Content & Photos

**[(00:02:16)](file:///Users/michal/workspace/stem/bottega/ecommerce-eventstorming.mov#t=00:02:16)** — The content manager works on **marketing materials** around the product. In parallel, dedicated personnel handle **adding photos** — they may take custom photos, get them from the vendor, or process them with AI. The goal is to add **high-quality photos** to the product description. Photos are stored in the system but not yet published.

### Review Process

**[(00:03:39)](file:///Users/michal/workspace/stem/bottega/ecommerce-eventstorming.mov#t=00:03:39)** — When the content manager decides the description is ready, they request a **pending review**. Another content manager is notified and can review and validate the description. The system aims to **streamline the creation process** and ensure **high-quality descriptions**.

**[(00:05:00)](file:///Users/michal/workspace/stem/bottega/ecommerce-eventstorming.mov#t=00:05:00)** — The review acts as a **quality gate** — the system should prevent publishing to the offer if issues are detected. However, the user can override the gate and push to review even with missing information. The quality gate must prevent publishing to the offer if there are problems.

### Publishing & Versioning

**[(00:07:26)](file:///Users/michal/workspace/stem/bottega/ecommerce-eventstorming.mov#t=00:07:26)** — Once the second content manager approves and no issues are found by the **description quality policy**, a new **product version published to offer** event is emitted. However, there is a nuance: an offer may have a **start of availability date**. The description may be accepted but not published immediately — it may be scheduled for a future date (e.g., a new iPhone model published on July 1st).

### Pricing

**[(00:08:36)](file:///Users/michal/workspace/stem/bottega/ecommerce-eventstorming.mov#t=00:08:36)** — The **sales** department manages pricing. They set the **pricing policy**, define price ranges, and apply discounts in given **date ranges**. Prices can be **scheduled or projected** for the future. When a price change occurs, an event is emitted to the outside world. Similar to descriptions, a scheduled price change is not immediately active — it may be delayed by a few days.

---

## 2. Browsing Offer

**[(00:10:44)](file:///Users/michal/workspace/stem/bottega/ecommerce-eventstorming.mov#t=00:10:44)** — Customers can **search** the offer, see **product details**, view **current prices**, and check the **stock of product** across all channels (mobile, web, phone). These are critical areas that must be always available and scaled for seasonality or product releases.

**[(00:12:06)](file:///Users/michal/workspace/stem/bottega/ecommerce-eventstorming.mov#t=00:12:06)** — Product descriptions and prices come from the **product catalog management** area. Product stock data comes from a separate **warehouse system** — an **off-the-shelf** product with APIs for stock checking, change notifications, reservations, and warehouse operations.

---

## 3. Basket Management

**[(00:12:55)](file:///Users/michal/workspace/stem/bottega/ecommerce-eventstorming.mov#t=00:12:55)** — The shopping experience begins with the **basket** (cart). Customers can **add to basket**, change quantities, or **remove from basket**. The customer sees the **basket content**, including **estimated delivery time** and **price**, with any **discounts** applied.

**[(00:13:41)](file:///Users/michal/workspace/stem/bottega/ecommerce-eventstorming.mov#t=00:13:41)** — Normal behavior: the system **does not reserve products** when placing them in the basket. Adding 10 pieces does not guarantee availability. Some reservations may exist, but not on that scale.

**[(00:14:04)](file:///Users/michal/workspace/stem/bottega/ecommerce-eventstorming.mov#t=00:14:04)** — If a product is removed from the offer while in the basket, it does not disappear — it is marked as unavailable (e.g., highlighted in red). The same applies if an item is **out of stock**. The customer may still order it, but should be aware that the delivery time will be longer than expected.

---

## 4. Checkout Process

**[(00:16:01)](file:///Users/michal/workspace/stem/bottega/ecommerce-eventstorming.mov#t=00:16:01)** — When the customer proceeds to **checkout**, the system presents **payment options**, **delivery options**, and **previous customer data / invoicing data** (for returning customers). The available **payment methods** and **delivery methods** depend on the basket content:

**[(00:18:13)](file:///Users/michal/workspace/stem/bottega/ecommerce-eventstorming.mov#t=00:18:13)** — Delivery to a **pickup point** (e.g., Paczkomat) may not work if the basket contains large items like a washing machine or refrigerator.

**[(00:18:56)](file:///Users/michal/workspace/stem/bottega/ecommerce-eventstorming.mov#t=00:18:56)** — **Third-party payment** (e.g., pay after a month) may be restricted based on customer history or partner database checks. These policies can be complex.

**[(00:20:07)](file:///Users/michal/workspace/stem/bottega/ecommerce-eventstorming.mov#t=00:20:07)** — The customer picks options, fills delivery and invoicing information, and then triggers the **terminal action** to process the checkout. This is a **guarded action** — all choices must be valid for the basket content.

**[(00:21:48)](file:///Users/michal/workspace/stem/bottega/ecommerce-eventstorming.mov#t=00:21:48)** — Once the checkout is processed, the system has an **obligation** to deliver the goods. The order becomes an **immutable document** — in normal processing, nothing should be added to it. Special cases (e.g., missing items during fulfillment) require manual negotiation with the customer outside the system.

---

## 5. Fulfillment (Order Processing)

**[(00:23:11)](file:///Users/michal/workspace/stem/bottega/ecommerce-eventstorming.mov#t=00:23:11)** — The fulfillment area handles packing and sending items to the customer.

### Order Processing

**[(00:23:24)](file:///Users/michal/workspace/stem/bottega/ecommerce-eventstorming.mov#t=00:23:24)** — The system shows a **list of orders** with their current status. A warehouse employee can **assign to order** to prevent duplicate processing.

**[(00:24:10)](file:///Users/michal/workspace/stem/bottega/ecommerce-eventstorming.mov#t=00:24:10)** — At the beginning, the system **registers the parcel** with a delivery company (e.g., DHL), receiving a **delivery number** and **vendor ID** attached to the order.

### Picking & Packing

**[(00:24:46)](file:///Users/michal/workspace/stem/bottega/ecommerce-eventstorming.mov#t=00:24:46)** — The assigned employee receives a **pick list** — an **optimized list** of which products to take from the warehouse and in what order to walk through the warehouse optimally. The employee scans the products, and the system marks those **positions as packed**.

### Delivery & Notifications

**[(00:25:35)](file:///Users/michal/workspace/stem/bottega/ecommerce-eventstorming.mov#t=00:25:35)** — When everything is correct, the employee marks the order as **ready for delivery**, updating the system and notifying the delivery company. The delivery company sends notifications for every state:

- parcel registered
- ready for delivery
- assigned to courier
- picked up
- in sorting facility
- every leg in the **delivery chain**
- customer received the product

**[(00:26:32)](file:///Users/michal/workspace/stem/bottega/ecommerce-eventstorming.mov#t=00:26:32)** — The customer gets notifications through the full chain of the delivery.

### Warehouse Integration

**[(00:27:19)](file:///Users/michal/workspace/stem/bottega/ecommerce-eventstorming.mov#t=00:27:19)** — The fulfillment area integrates with the warehouse to get the **location of product** and **reserve the product**. When the employee starts the pick list, they can **reserve the product** (e.g., "I am taking 2 units"), so no other employee takes the last units in the meantime. This is a **short-term reservation** to avoid collisions. The system also shows **stock changes** so the employee knows how many items are available at each location.

### Issues & Fallbacks

**[(00:29:35)](file:///Users/michal/workspace/stem/bottega/ecommerce-eventstorming.mov#t=00:29:35)** — The happy path assumes everything is correct. Fallback scenarios include:

- **Warehouse issues**: the system shows 2 units, but they are physically lost, destroyed, or in the wrong location.
- **Order on hold**: when an issue is detected, the order is placed on hold. A **read model** shows the **current issues** on the warehouse to other personnel. Another employee may need to find the missing pieces.

### Customer Notifications

**[(00:29:08)](file:///Users/michal/workspace/stem/bottega/ecommerce-eventstorming.mov#t=00:29:08)** — During fulfillment, the customer is notified about all order processing states. After each event from the delivery company, the **read model** is updated with new information, so the customer sees the current state of the order.

---

## Integration Summary

**[(00:12:06)](file:///Users/michal/workspace/stem/bottega/ecommerce-eventstorming.mov#t=00:12:06)** — External systems overview

| External System | Role |
|---------------|------|
| **Warehouse** | Off-the-shelf system. Provides stock data, reservations, and product locations |
| **CRM** | Stores customer data, invoicing data, order history. Receives new order events |
| **DHL** | Delivery company. Receives parcel registration, sends status updates |
| **Payment Provider** | Third-party payment processing. Checks customer eligibility for deferred payment |

---

## Key Decisions & Events

**[(00:06:41)](file:///Users/michal/workspace/stem/bottega/ecommerce-eventstorming.mov#t=00:06:41)** — Key decisions and quality gates

| Event | Trigger | Consequence |
|-------|---------|-------------|
| **blank draft created** | content manager starts new product | empty draft available |
| **description updated** | edit of product description | private change, not visible externally |
| **photo added in high-res formats** | photo team uploads photos | photos stored but not published |
| **pending review** | content manager requests review | another manager notified to review |
| **product version published to offer** | review approved, no policy issues | new version visible to customers |
| **product prices changed** | sales updates prices | event emitted to outside world |
| **add to basket** | customer adds item | item in cart, no reservation |
| **removed from basket** | customer removes item | item removed from cart |
| **delivery set-up** | checkout completed | delivery process initiated |
| **position packed** | employee scans item | item confirmed packed |
| **ready for delivery** | order fully packed | delivery company notified |
| **order is on hold** | issue detected | other personnel notified to resolve |

---

## Artifacts

- **Video:** `ecommerce-eventstorming.mov` (383MB)
- **Compressed:** `ecommerce-eventstorming.hevc.mp4` (223MB)
- **Subtitles:** `ecommerce-eventstorming.srt`
- **Improved Subtitles:** `ecommerce-eventstorming.improved.srt`
- **Improved Transcript:** `ecommerce-eventstorming.improved.txt`
- **This Summary:** `ecommerce-eventstorming.md`

---

*Generated from transcript with domain context from the event storming diagram.*
