import sys
import os

# Set environment variables for AWS and Database configuration
os.environ['AWS_ACCESS_KEY_ID'] = 'testing'
os.environ['AWS_SECRET_ACCESS_KEY'] = 'testing'
os.environ['AWS_SECURITY_TOKEN'] = 'testing'
os.environ['AWS_SESSION_TOKEN'] = 'testing'
os.environ['AWS_DEFAULT_REGION'] = 'us-east-1'

os.environ['DB_HOST'] = 'localhost'
os.environ['DB_USER'] = 'test_user'
os.environ['DB_PASSWORD'] = 'test_password'
os.environ['DB_NAME'] = 'test_db'
os.environ['DB_PORT'] = '3306'

# add 'src' to Python's path
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), '..')))

import json
import boto3
import pytest
from moto import mock_aws
from unittest.mock import patch

import main.lambda_1_processor as lambda_1_processor
from main.lambda_1_processor import lambda_handler as lambda_1_handler
from main.lambda_2_logger_push import lambda_handler as lambda_2_handler


@patch('main.lambda_1_processor.db_manager')
@patch('main.lambda_2_logger_push.db_manager')
@patch('main.lambda_2_logger_push.broadcast_to_user_ui')
@mock_aws
def test_end_to_end_workflow(mock_broadcast, mock_db_manager_2, mock_db_manager_1):
    """
    End-to-End Integration Test using Moto:
    1. Provisions a mock SQS input queue and SNS success topic locally.
    2. Simulates an incoming order message picked up by Lambda 1[cite: 3].
    3. Lambda 1 validates the order, interacts with the database manager, and publishes to SNS[cite: 2, 3].
    4. Intercepts the published SNS notification and passes it to Lambda 2[cite: 4].
    5. Verifies Lambda 2 logs the notification record and pushes updates to the user UI[cite: 2, 4].
    """
    mock_db_manager_1.create_order.return_value = 505
    mock_db_manager_2.add_notification.return_value = 909

    sqs_client = boto3.client('sqs', region_name='us-east-1')
    sns_client = boto3.client('sns', region_name='us-east-1')

    # 1. Create SNS Success Topic and bind its ARN directly to Lambda 1's module variable
    topic_response = sns_client.create_topic(Name='success-topic')
    topic_arn = topic_response['TopicArn']
    lambda_1_processor.SUCCESS_TOPIC_ARN = topic_arn

    # 2. Create SQS Input Queue for Lambda 1
    queue_response = sqs_client.create_queue(QueueName='order-processing-queue')
    queue_url = queue_response['QueueUrl']
    queue_attr = sqs_client.get_queue_attributes(QueueUrl=queue_url, AttributeNames=['QueueArn'])
    queue_arn = queue_attr['Attributes']['QueueArn']

    # Subscribe SQS Queue to SNS Topic
    sns_client.subscribe(
        TopicArn=topic_arn,
        Protocol='sqs',
        Endpoint=queue_arn
    )

    # 3. Simulate client sending an order message to SQS
    order_payload = {
        "user_id": 7,
        "items": [
            {"item_id": 2, "quantity": 1},
            {"item_id": 4, "quantity": 3}
        ]
    }
    
    sqs_client.send_message(
        QueueUrl=queue_url,
        MessageBody=json.dumps(order_payload)
    )

    messages = sqs_client.receive_message(QueueUrl=queue_url, MaxNumberOfMessages=1)
    sqs_record = messages['Messages'][0]

    lambda_1_event = {
        "Records": [
            {
                "messageId": sqs_record['MessageId'],
                "body": sqs_record['Body']
            }
        ]
    }

    # --- STEP 4: Execute Lambda 1 (Order Processor) ---
    l1_response = lambda_1_handler(lambda_1_event, None)
    assert l1_response["status"] == "Batch processing complete"

    mock_db_manager_1.create_order.assert_called_once_with(
        user_id=7,
        items=[{"item_id": 2, "quantity": 1}, {"item_id": 4, "quantity": 3}]
    )

    # --- STEP 5: Capture SNS publication & Trigger Lambda 2 (Logger & Push) ---
    delivered_messages = sqs_client.receive_message(QueueUrl=queue_url, MaxNumberOfMessages=1)
    sns_notification_record = delivered_messages['Messages'][0]
    
    sns_envelope = json.loads(sns_notification_record['Body'])
    sns_message_content = sns_envelope['Message']

    lambda_2_event = {
        "Records": [
            {
                "Sns": {
                    "Message": sns_message_content
                }
            }
        ]
    }

    l2_response = lambda_2_handler(lambda_2_event, None)
    assert l2_response["status"] == "Notifications logged and pushed successfully"

    mock_db_manager_2.add_notification.assert_called_once_with(
        user_id=7,
        message="Your order #505 has been successfully placed!",
        notification_type="SUCCESS"
    )

    mock_broadcast.assert_called_once_with(
        7,
        {
            "notification_id": 909,
            "type": "SUCCESS",
            "message": "Your order #505 has been successfully placed!"
        }
    )