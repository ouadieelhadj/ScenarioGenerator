import os
import sys
from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC


def required(name: str) -> str:
    value = os.getenv(name, "").strip()
    if not value or value == "LOCAL_SELECTOR_REQUIRED":
        raise SystemExit(f"Variable obligatoire absente ou non configurée: {name}")
    return value


def fill(wait: WebDriverWait, selector_name: str, value_name: str) -> None:
    selector = required(selector_name)
    value = required(value_name)
    element = wait.until(EC.element_to_be_clickable((By.CSS_SELECTOR, selector)))
    element.clear()
    element.send_keys(value)


def click(wait: WebDriverWait, selector_name: str) -> None:
    selector = required(selector_name)
    wait.until(EC.element_to_be_clickable((By.CSS_SELECTOR, selector))).click()


def main() -> int:
    options = webdriver.ChromeOptions()
    if os.getenv("WAY4_UI_HEADLESS", "NO") == "YES":
        options.add_argument("--headless=new")

    driver = webdriver.Chrome(options=options)
    wait = WebDriverWait(driver, 30)

    try:
        driver.get(required("WAY4_WB_URL"))
        fill(wait, "WAY4_WB_USERNAME_SELECTOR", "WAY4_WB_USERNAME")
        fill(wait, "WAY4_WB_PASSWORD_SELECTOR", "WAY4_WB_PASSWORD")
        click(wait, "WAY4_WB_LOGIN_SELECTOR")
        click(wait, "WAY4_WB_NEW_MERCHANT_SELECTOR")

        fill(wait, "WAY4_WB_LEGAL_NAME_SELECTOR", "WAY4_COMPANY_NAME")
        fill(wait, "WAY4_WB_TRADE_NAME_SELECTOR", "WAY4_TRADE_NAME")
        fill(wait, "WAY4_WB_REG_NUMBER_SELECTOR", "WAY4_CLIENT_REG_NUMBER")
        fill(wait, "WAY4_WB_MID_SELECTOR", "WAY4_MID")
        fill(wait, "WAY4_WB_MCC_SELECTOR", "WAY4_MCC")
        fill(wait, "WAY4_WB_ACCOUNT_CONTRACT_SELECTOR", "WAY4_ACCOUNT_CONTRACT_NUMBER")
        fill(wait, "WAY4_WB_TID_SELECTOR", "WAY4_DEVICE_CONTRACT_NUMBER")

        if os.getenv("WAY4_UI_CONFIRM_SUBMIT", "NO") == "YES":
            click(wait, "WAY4_WB_SUBMIT_SELECTOR")
            print("Demande soumise. Vérifier le workflow et le statut dans Way4.")
        else:
            print("Formulaire rempli sans soumission. WAY4_UI_CONFIRM_SUBMIT n'est pas YES.")
            if os.getenv("WAY4_UI_HEADLESS", "NO") == "YES":
                screenshot = os.getenv("WAY4_UI_SCREENSHOT", "way4-workbench-dry-run.png")
                driver.save_screenshot(screenshot)
                print(f"Capture de contrôle enregistrée: {screenshot}")
            else:
                input("Vérifiez le formulaire dans le navigateur, puis appuyez sur Entrée pour fermer.")
        return 0
    finally:
        driver.quit()


if __name__ == "__main__":
    sys.exit(main())
