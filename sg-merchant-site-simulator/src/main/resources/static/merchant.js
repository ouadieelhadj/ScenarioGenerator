(() => {
  "use strict";
  const $ = (id) => document.getElementById(id);
  const views = ["catalog", "product", "cart", "payment", "result"];
  const state = { configuration: null, catalog: [], cart: new Map(), selected: null, order: null };

  const digits = (value) => (value || "").replace(/\D/g, "");
  const money = (minor) => new Intl.NumberFormat("fr-MA", {
    minimumFractionDigits: 2, maximumFractionDigits: 2
  }).format(minor / 100) + " MAD";

  async function json(response) {
    const body = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(body.error || `Erreur HTTP ${response.status}`);
    return body;
  }

  function setStatus(message, kind) {
    $("configuration-status").textContent = message;
    $("configuration-status").className = "notice " + kind;
  }

  function show(view) {
    for (const name of views) $(`${name}-view`).hidden = name !== view;
    const active = view === "catalog" || view === "product" ? 0
      : view === "cart" ? 1 : view === "payment" ? 2 : 3;
    [...$("journey-steps").children].forEach((item, index) =>
      item.classList.toggle("active", index <= active));
    window.scrollTo({top: 0, behavior: "smooth"});
  }

  function product(productId) {
    return state.catalog.find((item) => item.id === productId);
  }

  function saveCart() {
    sessionStorage.setItem("atlas-cart", JSON.stringify([...state.cart]));
    $("cart-count").textContent = [...state.cart.values()]
      .reduce((total, quantity) => total + quantity, 0);
  }

  function loadCart() {
    try { state.cart = new Map(JSON.parse(sessionStorage.getItem("atlas-cart") || "[]")); }
    catch { state.cart = new Map(); }
    saveCart();
  }

  function element(tag, className, text) {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (text !== undefined) node.textContent = text;
    return node;
  }

  function renderCatalog() {
    const grid = $("product-grid");
    grid.replaceChildren();
    for (const item of state.catalog) {
      const card = element("article", "product-tile");
      const visual = element("div", "tile-visual", item.visualCode);
      const body = element("div", "tile-body");
      body.append(element("span", "product-badge", item.badge));
      body.append(element("h3", "", item.name));
      body.append(element("p", "", item.description));
      const footer = element("div", "tile-footer");
      footer.append(element("strong", "", money(item.unitPriceMinor)));
      const button = element("button", "", "Découvrir →");
      button.type = "button";
      button.addEventListener("click", () => openProduct(item.id));
      footer.append(button);
      body.append(footer);
      card.append(visual, body);
      grid.append(card);
    }
  }

  function openProduct(productId) {
    const item = product(productId);
    if (!item) return;
    state.selected = item;
    $("detail-code").textContent = item.visualCode;
    $("detail-badge").textContent = item.badge;
    $("detail-category").textContent = item.category;
    $("product-title").textContent = item.name;
    $("detail-description").textContent = item.description;
    $("detail-price").textContent = money(item.unitPriceMinor);
    $("detail-quantity").value = "1";
    const features = $("detail-features");
    features.replaceChildren(...item.features.map((feature) => element("li", "", feature)));
    show("product");
  }

  function cartTotal() {
    return [...state.cart.entries()].reduce((total, [id, quantity]) => {
      const item = product(id);
      return total + (item ? item.unitPriceMinor * quantity : 0);
    }, 0);
  }

  function renderCart() {
    const lines = $("cart-lines");
    lines.replaceChildren();
    for (const [id, quantity] of state.cart) {
      const item = product(id);
      if (!item) continue;
      const row = element("div", "cart-line");
      const description = element("div");
      description.append(element("strong", "", item.name));
      description.append(element("p", "", `${quantity} × ${money(item.unitPriceMinor)}`));
      row.append(description, element("strong", "", money(item.unitPriceMinor * quantity)));
      const remove = element("button", "", "Retirer");
      remove.type = "button";
      remove.addEventListener("click", () => { state.cart.delete(id); saveCart(); renderCart(); });
      row.append(remove);
      lines.append(row);
    }
    const empty = state.cart.size === 0;
    $("empty-cart").hidden = !empty;
    $("cart-summary").hidden = empty;
    $("checkout-button").hidden = empty;
    $("cart-total").textContent = money(cartTotal());
    show("cart");
  }

  function renderOrderSummary(order) {
    const lines = $("payment-order-lines");
    lines.replaceChildren();
    for (const item of order.lines) {
      const row = element("div", "order-summary-line");
      row.append(element("span", "", `${item.name} × ${item.quantity}`),
        element("strong", "", money(item.lineTotalMinor)));
      lines.append(row);
    }
    $("payment-total").textContent = money(order.totalMinor);
    $("locked-total").textContent = money(order.totalMinor);
    $("order-reference").textContent = `Commande ${order.orderReference}`;
  }

  async function createOrder() {
    const button = $("checkout-button");
    button.disabled = true;
    try {
      const items = [...state.cart].map(([productId, quantity]) => ({productId, quantity}));
      state.order = await json(await fetch("/api/merchant-site-simulator/v1/ui/orders", {
        method: "POST", headers: {"Content-Type": "application/json"},
        body: JSON.stringify({items})
      }));
      sessionStorage.setItem("atlas-order", JSON.stringify(state.order));
      renderOrderSummary(state.order);
      $("payment-method-step").hidden = false;
      $("card-step").hidden = true;
      show("payment");
    } catch (error) { setStatus(error.message, "error"); }
    finally { button.disabled = false; }
  }

  function brand(pan) {
    if (/^4/.test(pan)) return "Visa";
    if (/^(5[1-5]|2[2-7])/.test(pan)) return "Mastercard";
    return "Carte de test";
  }

  async function startPayment(event) {
    event.preventDefault();
    if (!state.order) return;
    const button = $("pay-button");
    button.disabled = true;
    button.textContent = "Initialisation 3-D Secure…";
    const request = {
      cardholder: $("cardholder").value,
      pan: digits($("pan").value),
      expiry: digits($("expiry").value)
    };
    try {
      const started = await json(await fetch(
        `/api/merchant-site-simulator/v1/ui/orders/${encodeURIComponent(state.order.orderId)}/payments`, {
          method: "POST", headers: {"Content-Type": "application/json"},
          body: JSON.stringify(request)
        }));
      $("pan").value = "";
      if (started.state === "CHALLENGE_REQUIRED") {
        window.location.assign(started.challengeUrl);
        return;
      }
      showReceipt({order: state.order, payment: started.purchase});
    } catch (error) { showFailure(error.message); }
    finally { button.disabled = false; button.textContent = "Payer et s’authentifier"; }
  }

  async function resumePayment(checkoutId) {
    show("result");
    $("result-title").textContent = "Finalisation de la commande…";
    $("result-message").textContent = "La preuve 3DS est vérifiée avant l’autorisation financière.";
    try {
      const receipt = await json(await fetch(
        `/api/merchant-site-simulator/v1/ui/orders/payments/${encodeURIComponent(checkoutId)}/complete`,
        {method: "POST"}));
      history.replaceState({}, "", "/");
      showReceipt(receipt);
    } catch (error) { showFailure(error.message); }
  }

  function showReceipt(receipt) {
    const payment = receipt.payment;
    const order = receipt.order || state.order;
    const approved = payment && payment.status === "APPROVED";
    $("result-panel").classList.toggle("error", !approved);
    $("result-icon").textContent = approved ? "✓" : "!";
    $("result-title").textContent = approved ? "Commande confirmée" : "Paiement refusé";
    $("result-message").textContent = approved
      ? "Votre paiement a été authentifié et autorisé."
      : "La commande n’a pas été débitée.";
    const receiptOrder = $("receipt-order");
    receiptOrder.replaceChildren();
    if (order) {
      receiptOrder.append(element("strong", "", order.orderReference));
      receiptOrder.append(element("span", "", `${order.lines.length} article(s) · ${money(order.totalMinor)}`));
    }
    const details = approved ? [
      ["Statut", payment.status], ["Code réponse", payment.responseCode],
      ["Autorisation", payment.authorizationCode || "—"],
      ["Route financière", payment.networkRoute], ["3-D Secure", payment.authenticationStatus]
    ] : [];
    const list = $("result-details");
    list.replaceChildren();
    for (const [name, value] of details) {
      const row = element("div");
      row.append(element("dt", "", name), element("dd", "", String(value)));
      list.append(row);
    }
    state.cart.clear(); saveCart(); sessionStorage.removeItem("atlas-order");
    show("result");
  }

  function showFailure(message) {
    showReceipt({order: state.order, payment: null});
    $("result-title").textContent = "Paiement non finalisé";
    $("result-message").textContent = message;
  }

  function reset() {
    state.order = null; state.selected = null; state.cart.clear(); saveCart();
    $("card-form").reset(); $("cardholder").value = "CLIENT TEST";
    show("catalog");
  }

  function bindEvents() {
    $("cart-button").addEventListener("click", renderCart);
    document.querySelectorAll('[data-action="catalog"]').forEach((button) =>
      button.addEventListener("click", () => show("catalog")));
    $("add-to-cart").addEventListener("click", () => {
      if (!state.selected) return;
      const quantity = Number($("detail-quantity").value);
      state.cart.set(state.selected.id, Math.min(5,
        (state.cart.get(state.selected.id) || 0) + quantity));
      saveCart(); renderCart();
    });
    $("checkout-button").addEventListener("click", createOrder);
    $("select-card").addEventListener("click", () => {
      $("payment-method-step").hidden = true; $("card-step").hidden = false;
    });
    $("back-to-methods").addEventListener("click", () => {
      $("payment-method-step").hidden = false; $("card-step").hidden = true;
    });
    $("pan").addEventListener("input", (event) => {
      const raw = digits(event.target.value).slice(0, 19);
      event.target.value = raw.replace(/(.{4})/g, "$1 ").trim();
      $("card-brand").textContent = raw.length >= 6
        ? `${brand(raw)} · routage BIN automatique`
        : "Le BIN déterminera automatiquement le routage.";
    });
    $("expiry").addEventListener("input", (event) => {
      const raw = digits(event.target.value).slice(0, 4);
      event.target.value = raw.length > 2 ? `${raw.slice(0, 2)}/${raw.slice(2)}` : raw;
    });
    $("card-form").addEventListener("submit", startPayment);
    $("new-order").addEventListener("click", reset);
  }

  async function initialize() {
    bindEvents(); loadCart();
    const parameters = new URLSearchParams(window.location.search);
    try {
      const [configuration, catalog] = await Promise.all([
        json(await fetch("/api/merchant-site-simulator/v1/ui/configuration")),
        json(await fetch("/api/merchant-site-simulator/v1/ui/catalog"))
      ]);
      state.configuration = configuration; state.catalog = catalog;
      renderCatalog();
      setStatus(`${configuration.storeName} ouvert · profil ${configuration.siteType.toLowerCase()} · sandbox prêt`, "ready");
      if (parameters.get("resume") === "3ds" && parameters.get("checkoutId")) {
        state.order = JSON.parse(sessionStorage.getItem("atlas-order") || "null");
        await resumePayment(parameters.get("checkoutId"));
      } else { show("catalog"); }
    } catch (error) { setStatus(error.message, "error"); }
  }

  initialize();
})();
