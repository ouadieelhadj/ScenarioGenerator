(() => {
  "use strict";
  const parameters = new URLSearchParams(window.location.search);
  const displayEndpoint = "/api/3ds/network/v1/external-acs/sandbox/display";
  const challengeEndpoint = "/api/3ds/network/v1/external-acs/creq";
  const required = ["threeDSServerTransId", "dsTransId", "acsTransId", "messageVersion", "returnUrl"];
  const message = document.getElementById("message");

  function fail(text) { message.textContent = text; message.className = "message error"; }
  async function json(response) {
    const body = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(body.error || `Erreur HTTP ${response.status}`);
    return body;
  }
  async function initialize() {
    if (required.some((name) => !parameters.get(name))) {
      fail("Contexte 3DS incomplet. Revenez au site marchand.");
      document.getElementById("confirm").disabled = true;
      return;
    }
    document.getElementById("transaction-reference").textContent = parameters.get("threeDSServerTransId");
    try {
      const display = await json(await fetch(displayEndpoint));
      document.getElementById("issuer").textContent = display.issuer;
      document.getElementById("sandbox-otp").textContent = display.otp;
      message.textContent = "SMS simulé envoyé. Saisissez le code affiché.";
    } catch (error) { fail(error.message); }
  }
  document.getElementById("challenge-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    const button = document.getElementById("confirm");
    button.disabled = true;
    message.textContent = "Vérification de l’OTP…";
    message.className = "message";
    try {
      const result = await json(await fetch(challengeEndpoint, {
        method: "POST", headers: {"Content-Type": "application/json"},
        body: JSON.stringify({
          messageType: "CReq",
          messageVersion: parameters.get("messageVersion"),
          threeDSServerTransId: parameters.get("threeDSServerTransId"),
          dsTransId: parameters.get("dsTransId"),
          acsTransId: parameters.get("acsTransId"),
          challengeData: document.getElementById("otp").value.trim()
        })
      }));
      if (result.transStatus !== "Y" || result.challengeCompletionInd !== true) throw new Error("OTP refusé par l’ACS");
      message.textContent = "Authentification réussie. Retour vers le marchand…";
      message.className = "message ok";
      setTimeout(() => window.location.replace(parameters.get("returnUrl")), 650);
    } catch (error) { fail(error.message); button.disabled = false; }
  });
  initialize();
})();
