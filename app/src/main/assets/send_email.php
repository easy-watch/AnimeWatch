<?php
use PHPMailer\PHPMailer\PHPMailer;
use PHPMailer\PHPMailer\Exception;

require 'PHPMailer/src/Exception.php';
require 'PHPMailer/src/PHPMailer.php';
require 'PHPMailer/src/SMTP.php';

header("Access-Control-Allow-Origin: *");
header("Access-Control-Allow-Methods: POST");
header("Access-Control-Allow-Headers: Content-Type");

if ($_SERVER["REQUEST_METHOD"] == "POST") {
    $userEmail = $_POST["userEmail"];
    $userMessage = $_POST["userMessage"];
    $recipientEmail = "gamingalon123@gmail.com";
    $subject = "Contact Us Message";
    $message = "Message from: $userEmail\n\n$userMessage";

    $mail = new PHPMailer(true);
    try {
        // Gmail SMTP সেটআপ
        $mail->isSMTP();
        $mail->Host = 'smtp.gmail.com';
        $mail->SMTPAuth = true;
        $mail->Username = 'gamingalon123@gmail.com'; // আপনার Gmail ঠিকানা
        $mail->Password = 'gqph vnip troh lucf'; // আপনার App Password এখানে
        $mail->SMTPSecure = PHPMailer::ENCRYPTION_STARTTLS;
        $mail->Port = 587;

        // ইমেল সেটআপ
        $mail->setFrom($userEmail);
        $mail->addAddress($recipientEmail);
        $mail->Subject = $subject;
        $mail->Body = $message;

        $mail->send();
        echo json_encode(array("status" => "success", "message" => "ইমেল সফলভাবে পাঠানো হয়েছে!"));
    } catch (Exception $e) {
        echo json_encode(array("status" => "error", "message" => "ইমেল পাঠাতে ব্যর্থ। ত্রুটি: {$mail->ErrorInfo}"));
    }
} else {
    echo json_encode(array("status" => "error", "message" => "অবৈধ রিকোয়েস্ট মেথড।"));
}
?>