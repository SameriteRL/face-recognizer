"use client";

import { useState } from "react";
import axios from "axios";

export default function Home() {

  const [faceFile, setFaceFile] = useState(null);
  const [testFile, setTestFile] = useState(null);
  const [imageSrc, setImageSrc] = useState("");

  const handleFaceFileChange = (e) => {
    setFaceFile(e.target.files[0]);
  };

  const handleTestFileChange = (e) => {
    setTestFile(e.target.files[0]);
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    const formData = new FormData();
    formData.append("faceImg", faceFile);
    formData.append("testImg", testFile);
    try {
      const response = await axios.post(
        "http://localhost:8080/submit",
        formData,
        {
          headers: {"Content-Type": "multipart/form-data"},
          responseType: "blob"
        }
      );
      const url = URL.createObjectURL(response.data);
      console.log("File uploaded successfully", response.data);
      setImageSrc(url);
    }
    catch (error) {
      console.error("Error uploading file", error);
    }
  };

  return (
    <div>
      <h1>Upload a face</h1>
      <form>
        <input type="file" onChange={handleFaceFileChange} />
      </form>
      <h1>Upload a test image</h1>
      <form>
        <input type="file" onChange={handleTestFileChange} />
      </form>
      <button onClick={handleSubmit}>Submit</button>
      {
        imageSrc ?
        <img src={imageSrc} alt="Dynamically Generated" width={500} height={500}/>
        :
        <p>Waiting for output...</p>
      }
    </div>
  );
}