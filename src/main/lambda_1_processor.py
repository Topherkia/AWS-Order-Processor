import json
import os
import boto3
from main.db_manager import DatabaseManager

sns_client = boto3.client('sns')
db_manager = DatabaseManager()
SUCCESS_TOPIC_ARN = os.environ.get('SNS_SUCCESS_TOPIC_ARN')

def lambda_handler(event, context):
    """
    Lambda 1: Order Processor triggered by Amazon SQS.
    """
    for record in event.get('Records', []):
        try:
            # Parse message payload from SQS
            payload = json.loads(record['body'])
            user_id = payload.get('user_id')
            items = payload.get('items', []) # Expected list of {'item_id': int, 'quantity': int}

            if not user_id or not items:
                raise ValueError("Invalid payload structure: missing 'user_id' or 'items'.")

            # Perform DB write operation using the OOP class
            order_id = db_manager.create_order(user_id=user_id, items=items)
            print(f"Successfully processed order ID: {order_id} for user ID: {user_id}")

            # Publish success event to SNS 1 (Success Topic)
            success_message = {
                "user_id": user_id,
                "order_id": order_id,
                "status": "SUCCESS",
                "message": f"Your order #{order_id} has been successfully placed!"
            }
            
            sns_client.publish(
                TopicArn=SUCCESS_TOPIC_ARN,
                Message=json.dumps(success_message),
                Subject="Order Success Confirmation"
            )

        except Exception as e:
            print(f"Error processing order record {record.get('messageId')}: {str(e)}")
            # Raising the exception ensures SQS triggers retry / DLQ routing mechanism
            raise e

    return {"status": "Batch processing complete"}