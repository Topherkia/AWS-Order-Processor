import json
import os
import boto3
from db_manager import DatabaseManager

sns_client = boto3.client('sns')
db_manager = DatabaseManager()
SUCCESS_TOPIC_ARN = os.environ.get('SNS_SUCCESS_TOPIC_ARN')

def lambda_handler(event, context):
    """
    Lambda 1: Order Processor parsing the new SQS payload format with simulation support.
    """
    for record in event.get('Records', []):
        try:
            # Parse message payload from SQS
            payload = json.loads(record['body'])
            
            # Extract fields based on the new schema format
            user_id = payload.get('user_id') # Assumes user_id is passed alongside lines or derived from context
            order_id_ext = payload.get('orderId')
            lines = payload.get('lines', [])
            simulate_mode = payload.get('simulate')

            if not user_id or not lines:
                raise ValueError("Invalid payload structure: missing 'user_id' or 'lines'.")

            # Simulation logic for testing retries and DLQ routing
            if simulate_mode == "TRANSIENT":
                raise RuntimeError(f"Simulated transient error for order ID {order_id_ext}. Triggering retry...")

            # Perform DB write operation using the OOP class
            order_id = db_manager.create_order(user_id=user_id, lines=lines)
            print(f"Successfully processed external order {order_id_ext} -> DB order ID: {order_id}")

            # Publish success event to SNS 1 (Success Topic)
            success_message = {
                "user_id": user_id,
                "order_id": order_id,
                "external_order_id": order_id_ext,
                "status": "SUCCESS",
                "message": f"Your order #{order_id_ext} has been successfully placed!"
            }
            
            sns_client.publish(
                TopicArn=SUCCESS_TOPIC_ARN,
                Message=json.dumps(success_message),
                Subject="Order Success Confirmation"
            )

        except Exception as e:
            print(f"Error processing record {record.get('messageId')}: {str(e)}")
            # Raising the exception forces SQS to retry or route to DLQ once threshold is met
            raise e

    return {"status": "Batch processing complete"}