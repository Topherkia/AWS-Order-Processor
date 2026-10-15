import os
import pymysql
from datetime import datetime

class DatabaseManager:
    """
    Shared database utility class for RDS MySQL interactions within an AWS VPC.
    """
    def __init__(self):
        self.host = os.environ.get('DB_HOST')
        self.user = os.environ.get('DB_USER')
        self.password = os.environ.get('DB_PASSWORD')
        self.database = os.environ.get('DB_NAME')
        self.port = int(os.environ.get('DB_PORT', 3306))

    def _get_connection(self):
        return pymysql.connect(
            host=self.host,
            user=self.user,
            password=self.password,
            database=self.database,
            port=self.port,
            cursorclass=pymysql.cursors.DictCursor,
            connect_timeout=5
        )

    def create_order(self, user_id: int, lines: list) -> int:
        """
        Inserts a new order and its associated order items atomically.
        lines format: [{'itemId': 3, 'quantity': 1}, ...]
        """
        connection = self._get_connection()
        try:
            with connection.cursor() as cursor:
                # 1. Insert into Orders table
                order_query = "INSERT INTO `Orders` (`created_at`, `user_id`) VALUES (%s, %s)"
                cursor.execute(order_query, (datetime.utcnow(), user_id))
                order_id = cursor.lastrowid

                # 2. Insert into order_items table using 'itemId'
                item_query = "INSERT INTO `order_items` (`order_id`, `item_id`, `quantity`) VALUES (%s, %s, %s)"
                for line in lines:
                    cursor.execute(item_query, (order_id, line['itemId'], line['quantity']))

            connection.commit()
            return order_id
        except Exception as e:
            connection.rollback()
            raise e
        finally:
            connection.close()

    def add_notification(self, user_id: int, message: str, notification_type: str) -> int:
        connection = self._get_connection()
        try:
            with connection.cursor() as cursor:
                query = "INSERT INTO `Notification` (`user_id`, `message`, `type`) VALUES (%s, %s, %s)"
                cursor.execute(query, (user_id, message, notification_type))
            connection.commit()
            return cursor.lastrowid
        except Exception as e:
            connection.rollback()
            raise e
        finally:
            connection.close()