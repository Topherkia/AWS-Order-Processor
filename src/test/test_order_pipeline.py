import json
import uuid
import os
import boto3
import pytest
from dotenv import load_dotenv

# Load environment variables from the .env file in the root directory
load_dotenv()

# Read from environment
AWS_REGION = os.environ.get('AWS_DEFAULT_REGION', 'us-east-1')
AWS_ACCESS_KEY_ID = os.environ.get('AWS_ACCESS_KEY_ID')
AWS_SECRET_ACCESS_KEY = os.environ.get('AWS_SECRET_ACCESS_KEY')
AWS_SESSION_TOKEN = os.environ.get('AWS_SESSION_TOKEN')
SQS_QUEUE_URL = os.environ.get('SQS_QUEUE_URL')

# Initialize boto3 client using loaded credentials
sqs = boto3.client('sqs', region_name=AWS_REGION)

def send_test_order(simulate_type=None):
    """
    Helper function to create and send a test order payload to the Main SQS Queue.
    """
    external_order_id = str(uuid.uuid4())
    
    order_payload = {
        "user_id": 1,  
        "orderId": external_order_id,
        "lines": [
            {"itemId": 3, "quantity": 1},
            {"itemId": 4, "quantity": 1}
        ],
        "simulate": simulate_type
    }

    response = sqs.send_message(
        QueueUrl=SQS_QUEUE_URL,
        MessageBody=json.dumps(order_payload)
    )
    return response

def test_send_normal_order():
    """Pytest test case for normal success order flow."""
    response = send_test_order(simulate_type=None)
    assert response.get('ResponseMetadata', {}).get('HTTPStatusCode') == 200

def test_send_transient_order():
    """Pytest test case for transient error / retry flow."""
    response = send_test_order(simulate_type="TRANSIENT")
    assert response.get('ResponseMetadata', {}).get('HTTPStatusCode') == 200