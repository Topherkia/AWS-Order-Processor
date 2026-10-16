import json
import os
import boto3
import logging
from botocore.config import Config
from db_manager import DatabaseManager

db_manager = DatabaseManager()

def get_items(payload):
    return db_manager.get_items()
def get_orders(payload):
    return db_manager.get_orders(payload['userId'])
def get_notifications(payload):
    return db_manager.get_notifications(payload['userId'])
def login(payload):
    return db_manager.login(payload['email'])
def logout(payload):
    return db_manager.logout()

    
TEST_USER_ID = 1  # hardcoded for the whole project


operations = {
    'get_items': get_items,
    'get_orders': get_orders,
    'get_notifications': get_notifications,
    'login': login,
    'logout': logout
}

def lambda_handler(event, context):
    body = json.loads(event["body"])

    operation = body["operation"]
    payload = body["payload"]
    result = operations[operation](payload)


    return {
        'statusCode': 200,
        'body': payload
    }