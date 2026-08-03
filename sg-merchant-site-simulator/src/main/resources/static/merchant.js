(() => {
  "use strict";
  const $ = (id) => document.getElementById(id);
  const form = $("checkout-form");
  const status = $("configuration-status");
  const payButton = $("pay-button");
  let configuration;

  const digits = (value) => value.replace(/\D/g, "");
  const uuid = () => crypto.randomUUID();
  const money = (value) => new Intl.NumberFormat("fr-MA", {
    minimumFractionDigits: 2, maximumFractionDigits: 2
  }).format(value) + " MAD";

  function setStatus(message, kind) {
    status.textContent = message;
    status.className = "notice " + kind;
  }

  function brand(pan) {
    if (/^4/.test(pan)) return "Visa";
    if (/^(5[1-5]|2[2-7])/.test(pan)) return "Mastercard";
    return "Carte de test";
  }

  $("pan").addEventListener("input", (event) => {
    const raw = digits(event.target.value).slice(0, 19);
    event.target.value = raw.replace(/(.{4})/g, "$1 ").trim();
    $("card-brand").textContent = raw.length >= 6
      ? brand(raw) + " · routage déterminé par le BIN"
      : "Le BIN déterminera le routage réel.";
  });
  $("expiry").addEventListener("input", (event) => {
    const raw = digits(event.target.value).slice(0, 4);
    event.target.value = raw.length > 2 ? raw.slice(0, 2) + "/" + raw.slice(2) : raw;
  });
  $("amount").addEventListener("input", () => {
    $("total-value").textContent = money(Number($("amount").value || 0));
  });

  function showResult(result, error) {
    $("checkout-panel").hidden = true;
    const panel = $("result-panel");
    panel.hidden = false;
    panel.classList.toggle("error", Boolean(error));
    $("result-icon").textContent = error ? "!" : "✓";
    $("result-title").textContent = error ? "Paiement non finalisé" : "Paiement approuvé";
    $("result-message").textContent = error || "L’authentification et l’autorisation sont terminées.";
    const details = error ? [] : [
      ["Statut", result.status],
      ["Code réponse", result.responseCode],
      ["Autorisation", result.authorizationCode || "—"],
      ["Route", result.networkRoute],
      ["3-D Secure", result.authenticationStatus]
    ];
    const detailList = $("result-details");
    detailList.replaceChildren();
    for (const [name, value] of details) {
      const row = document.createElement("div");
      const term = document.createElement("dt");
      const description = document.createElement("dd");
      term.textContent = name;
      description.textContent = String(value);
      row.append(term, description);
      detailList.append(row);
    }
    $("pan").value = "";
  }

  async function json(response) {
    const body = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(body.error || `Erreur HTTP ${response.status}`);
    return body;
  }

  async function resumeCheckout(checkoutId) {
    $("checkout-panel").hidden = true;
    $("result-panel").hidden = false;
    $("result-title").textContent = "Finalisation de l’achat…";
    $("result-message").textContent = "La preuve 3DS est contrôlée avant l’autorisation.";
    try {
      const result = await json(await fetch(
        `/api/merchant-site-simulator/v1/ui/checkouts/${encodeURIComponent(checkoutId)}/complete`,
        {method: "POST"}));
      history.replaceState({}, "", "/");
      showResult(result, null);
    } catch (error) {
      showResult({}, error.message);
    }
  }

  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    payButton.disabled = true;
    payButton.textContent = "Initialisation 3-D Secure…";
    const transactionId = uuid();
    const expiry = digits($("expiry").value);
    const flow = $("flow").value;
    const request = {
      transactionId,
      correlationId: `merchant-${transactionId}`,
      idempotencyKey: `merchant-idem-${transactionId}`,
      acquirerId: configuration.acquirerId,
      profileId: configuration.profileId,
      merchantOrderId: `WEB-${Date.now()}`,
      amountMinor: Math.round(Number($("amount").value) * 100),
      currency: configuration.currency,
      pan: digits($("pan").value),
      expiry,
      networkRoute: $("network-route").value,
      siteType: $("site-type").value,
      threeDsProgram: flow === "NOT_REQUESTED" ? null : $("program").value,
      threeDsFlow: flow,
      issuerMode: flow === "NOT_REQUESTED" ? null : $("issuer-mode").value,
      challengeData: null
    };
    try {
      const started = await json(await fetch(
        "/api/merchant-site-simulator/v1/ui/checkouts", {
          method: "POST", headers: {"Content-Type": "application/json"},
          body: JSON.stringify(request)
        }));
      if (started.state === "CHALLENGE_REQUIRED") {
        window.location.assign(started.challengeUrl);
        return;
      }
      showResult(started.purchase, null);
    } catch (error) {
      showResult({}, error.message);
    } finally {
      payButton.disabled = false;
      payButton.textContent = "Payer maintenant";
    }
  });

  async function initialize() {
    const parameters = new URLSearchParams(window.location.search);
    if (parameters.get("resume") === "3ds" && parameters.get("checkoutId")) {
      await resumeCheckout(parameters.get("checkoutId"));
      return;
    }
    try {
      configuration = await json(await fetch(
        "/api/merchant-site-simulator/v1/ui/configuration"));
      setStatus("Profil marchand actif · paiement sandbox prêt", "ready");
      payButton.disabled = false;
    } catch (error) {
      setStatus(error.message + " — lancez l’étape de provisionnement.", "error");
    }
  }
  initialize();
})();
