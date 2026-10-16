import json
import os
import time
import hashlib
import urllib.parse
import urllib.request
from db_manager import DatabaseManager

db_manager = DatabaseManager()

BAIDU_API_KEY = os.environ.get('BAIDU_API_KEY')
BAIDU_SECRET_KEY = os.environ.get('BAIDU_SECRET_KEY')

def generate_baidu_sign(method: str, url: str, params: dict, secret_key: str) -> str:
    """
    Generates the required HMAC/MD5 signature for Baidu Cloud Push API requests.
    """
    # Sort parameters alphabetically by key
    sorted_params = sorted(params.items())
    param_string = "".join([f"{k}={v}" for k, v in sorted_params])
    
    # Concatenate method, URL path, parameter string, and secret key
    base_string = method.upper() + url + param_string + secret_key
    
    # URL encode and compute MD5 hash
    encoded_string = urllib.parse.quote(base_string, safe='')
    return hashlib.md5(base_string.encode('utf-8')).hexdigest()

def send_baidu_push(user_id: int, title: str, description: str):
    """
    Sends a push notification to an Android user via Baidu Cloud Push REST API.
    """
    if not BAIDU_API_KEY or not BAIDU_SECRET_KEY:
        print("[Baidu Push Mock] Baidu credentials not configured. Skipping external push.")
        return

    rest_url = "http://api.tuisong.baidu.com/rest/3.0/push/single_device"
    
    # Baidu message structure
    message_content = {
        "title": title,
        "description": description,
        "notification_basic_style": 7,
        "open_type": 0
    }

    # Common parameters required by Baidu API
    params = {
        "apikey": BAIDU_API_KEY,
        "channel_id": f"user_channel_{user_id}", # Mapped from your user database/registration
        "message_type": 1, # 1 for notification, 0 for message
        "message": json.dumps(message_content),
        "timestamp": int(time.time())
    }

    # Generate request signature
    signature = generate_baidu_sign("POST", "/rest/3.0/push/single_device", params, BAIDU_SECRET_KEY)
    params["sign"] = signature

    try:
        data = urllib.parse.urlencode(params).encode('utf-8')
        req = urllib.request.Request(rest_url, data=data, method='POST')
        with urllib.request.urlopen(req) as response:
            result = json.loads(response.read().decode('utf-8'))
            print(f"Baidu Push Response for user {user_id}: {result}")
    except Exception as e:
        print(f"Failed to send Baidu push notification for user {user_id}: {str(e)}")

def lambda_handler(event, context):
    """
    Lambda 2: DB Logger & Baidu Push triggered by SNS Success/DLQ topics[cite: 2].
    """
    for record in event.get('Records', []):
        try:
            # Extract SNS message payload
            sns_message_raw = record.get('Sns', {}).get('Message', '{}')
            payload = json.loads(sns_message_raw)

            user_id = payload.get('user_id')
            message_text = payload.get('message', 'Notification update regarding your order.')
            notification_type = payload.get('status', 'INFO') # SUCCESS or FAILED

            if not user_id:
                print(f"Received notification payload without user_id: {payload}")
                continue

            # 1. Database Logging into RDS notifications table[cite: 1, 2]
            notif_id = db_manager.add_notification(
                user_id=user_id,
                message=message_text,
                notification_type=notification_type
            )
            print(f"Successfully logged notification ID {notif_id} for user ID {user_id} [{notification_type}]")

            # 2. Forward push notification to Android device via Baidu
            title = "Order Update" if notification_type == "SUCCESS" else "Order Alert"
            send_baidu_push(user_id=user_id, title=title, description=message_text)

        except Exception as e:
            print(f"Error handling notification event: {str(e)}")
            raise e

    return {"status": "Notifications logged and pushed via Baidu successfully"}