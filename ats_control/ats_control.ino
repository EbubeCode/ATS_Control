

       /*model select
      //ACS712
      0-"ACS712ELCTR-05B",
      1-"ACS712ELCTR-20A",
      2-"ACS712ELCTR-30A",
      
      //ACS758
      3,// for ACS758LCB-050B
      4,// for ACS758LCB-050U
      5,// for ACS758LCB-100B
      6,// for ACS758LCB-100U
      7,// for ACS758KCB-150B
      8,// for ACS758KCB-150U
      9,// for ACS758ECB-200B
      10,// for ACS758ECB-200U 
      
      ///ACS770
      11,// for ACS770x-050B ///      
      12,// for ACS770x-050U  
      13,// for ACS770x-100B
      14,// for ACS770x-100U
      15,// for ACS770x-150B
      16,// for ACS770x-150U
      17,// for ACS770x-200B  
      18,// for ACS770x-200U  
      

      19 for "ACS732KLATR-20AB",
      20 for "ACS732KLATR-40AB",
      21 for "ACS732KLATR-65AB", 
      22 for "ACS732KLATR-65AU",   
      23 for "ACS732KLATR-75AB", 

      24 for "ACS733KLATR-20AB",
      25 for "ACS733KLATR-40AB",
      26 for "ACS733KLATR-40AU", 
      27 for "ACS733KLATR-65AU",
      */
#include <LiquidCrystal_I2C.h>
LiquidCrystal_I2C lcd(0x27, 20, 4);
#include <Robojax_AllegroACS_Current_Sensor.h>
#include<Wire.h>

//*************************measuring current using ACS712***********************
const int VIN = A1; // define the Arduino pin A0 as voltage input (V in)
const float VCC   = 5.04;// supply voltage
const int MODEL = 2;   // enter the model (see above list)
Robojax_AllegroACS_Current_Sensor robojax(MODEL,VIN);

 //**************************Measuring Voltage using Stepdown transformer
 int load_volt_sensor = A0; //Analog Input
 float load_vd = 0.0; //Voltage In after voltage divider
 float load_rect_volt = 0.0;       //Actual voltage after calculation
 float CalVal = 11.00; //Voltage divider calibration value
 float transformer_turn_ratio = 19.052;
 float load_trans_sec_volt = 0.0;
 int load_trans_pri_volt = 0.0;
 float rms_voltage_factor = 1.4;

//*****************************Timing***************************
const long interval = 200;//Interval to read voltages


//***************************input pin declearation********************
const int grid_line_input = 12;
const int gen_line_input = 13;

//***************************output pin declearation********************
const int grid_line_output = 4;
const int gen_line_output = 6;
const int solar_line_output = 5;
const int gen_on_switch = 7;
const int gen_start_switch = 8;
const int buzzer = 9;

//**********variable for reading input phase status. This is used to keeping all input at "0" zero or off state****************
bool grid_state = false;
bool gen_state = false;
bool solar_state = false;
bool all_source_state = false;
bool alarmstate = true;
bool solaralarmstate = false;
bool Initializing = true;

void setup()
{
  pinMode(grid_line_input, INPUT);
  pinMode(gen_line_input, INPUT);

  pinMode(grid_line_output, OUTPUT);
  pinMode(gen_line_output, OUTPUT);
  pinMode(solar_line_output, OUTPUT);
  pinMode(gen_on_switch, OUTPUT);
  pinMode(gen_start_switch, OUTPUT);
  pinMode(buzzer, OUTPUT);
  pinMode(LED_BUILTIN, OUTPUT);
 
  pinMode(A0,INPUT);
  pinMode(A1, INPUT);
// PLEASE NOTE ALL RELAY USED IN THIS PROJECT ARE ACTIVE LOW
  digitalWrite ( grid_line_output,LOW);
  digitalWrite ( gen_line_output,LOW);
  digitalWrite ( solar_line_output,LOW);
  digitalWrite (gen_on_switch,LOW);
  digitalWrite (gen_start_switch, LOW);
  digitalWrite ( buzzer,LOW);
    
  //********************Initialise serial monitor. This will help display what is happening; on the serial monitor****************
  Serial.begin(9600);
  Serial.println("3 POWER SOURCE AUTOMATIC CHANGE OVER SYSTEM");
  Serial.println("Processing available SOURCE lines...");
    //***************initialize the LCD*******************
  lcd.begin(20,4); 
  lcd.backlight();
  lcd.print("3 POWER SOURCE ATS");
  lcd.setCursor(0,1);
  lcd.print("    SYSTEM"); 
  delay(125);
  lcd.setCursor(0,2);
  lcd.print("Initializing..."); 
  lcd.blink();
  delay(250);


}

void turnOnBuzzer();
void printLCD(String source, double AC_Current, double POWER);
void toggleGen(int toggle, double AC_Current, double POWER);

void loop() 
{
  
  //**********************checking for the state of each input******************************
  int gridline_current_state = digitalRead(grid_line_input);
  int genline_current_state = digitalRead(gen_line_input);

    //***************************Measure Voltage*******************************************
  load_volt_sensor = analogRead(A0); //reading the sensor values in 10bit resolution 0-1023
  load_vd = (load_volt_sensor * 5.00)/1024.00; //Convert 10bit input to an actual voltage 
  load_rect_volt = (load_vd - 0.038)* CalVal; //calculate voltage divider and calibration value to get output dc voltage
  load_trans_sec_volt = (load_rect_volt / rms_voltage_factor ); //reconvert DC voltage to AC voltage from transformer secondary turn 
  load_trans_pri_volt = ( load_trans_sec_volt * transformer_turn_ratio); //calculating the actual AC MAIN voltage for this source

  //************************* Measure Power ******************************************
  int Power = (load_trans_pri_volt * robojax.getCurrentAverage(300));
  
  // ***********************Output voltage and current lcd info************************
  Serial.print("Current: ");
  Serial.print(robojax.getCurrent(),3); // print the current with 3 decimal places
  lcd.setCursor(0, 1);
  lcd.print("Voltage: ");
  lcd.print(load_trans_pri_volt);
  lcd.print("V ");  // unit for voltage to be measured
  lcd.setCursor (0,2);
  lcd.print("Current: ");
  lcd.print(robojax.getCurrentAverage(300),3);
  lcd.print("A "); //unit for the current to be measured
  lcd.setCursor(0,3);
  lcd.print("Power: ");
  lcd.print(Power);
  lcd.print("W"); //unit for the current to be measured
  lcd.noBlink();
  
    
  
  //******************************* Check power sources ********************************

  if (gridline_current_state == HIGH && alarmstate) {
    Serial.println("Grid is ON");
    
    turnOnBuzzer();
    digitalWrite ( gen_line_output,LOW);
    digitalWrite ( solar_line_output,LOW);
    toggleGen(0, robojax.getCurrentAverage(300), Power); // the gen_auto_switch is acive LOW
    digitalWrite (grid_line_output,HIGH);
    alarmstate = false;
    lcd.setCursor (0,0); // set to line 1, char 0  
    lcd.print("GRID is on          ");
    
    
  } else if (gridline_current_state == LOW  && (!alarmstate || Initializing)) {

    turnOnBuzzer();
    digitalWrite ( gen_line_output,LOW);
    digitalWrite ( grid_line_output,LOW);
    digitalWrite (gen_on_switch,HIGH); // the gen_auto_switch is acive LOW
    digitalWrite ( solar_line_output,HIGH);
    Serial.println("PV is ON");
    lcd.setCursor (0,0); // set to line 1, char 0  
    lcd.print("PV is on            ");
    toggleGen(1, robojax.getCurrentAverage(300), Power);
    alarmstate = true;
  } else if (gridline_current_state == LOW && genline_current_state == LOW) {
    digitalWrite ( gen_line_output,LOW);
    digitalWrite ( grid_line_output,LOW);
    digitalWrite (gen_on_switch,LOW); // the gen_auto_switch is acive LOW
    digitalWrite ( solar_line_output,HIGH);
    Serial.println("PV is ON");
    lcd.setCursor (0,0); // set to line 1, char 0  
    lcd.print("PV is on            ");
  }
  Initializing = false;

}
void turnOnBuzzer() {
  digitalWrite ( buzzer,HIGH);
  delay(250);
  digitalWrite ( buzzer,LOW);
}

void printLCD(String source, double AC_Current, double Power) {
  lcd.clear();
  lcd.setCursor (0,0); // set to line 1, char 0  
  

}

bool checkGenState();

void toggleGen(int toggle, double AC_Current, double Power) {
  if (toggle == 0) {
    digitalWrite(gen_start_switch, LOW);
    digitalWrite(gen_on_switch, LOW);
    return;
  }

  if (checkGenState()) {
    digitalWrite(gen_on_switch, HIGH);
    digitalWrite(gen_start_switch, HIGH);
    if (digitalRead(grid_line_input) == HIGH) return; // ensure that the grid isn't back on before going in to delay
    delay(5000); // delay 10 seconds and check if the gen has started
    digitalWrite(gen_start_switch, LOW);
    if (digitalRead(grid_line_input) == HIGH) return; // ensure that the grid isn't back on before going in to delay
    delay(7000); // delay for 5 seconds and allow to settle (started or not)
    if (digitalRead(gen_line_input) == LOW && toggle <= 2) {
      toggleGen(toggle + 1, AC_Current, Power);
    } 
    else if (digitalRead(gen_line_input) == HIGH) {
      turnOnBuzzer();
      digitalWrite (solar_line_output, LOW);
      digitalWrite ( grid_line_output, LOW);
      digitalWrite (gen_on_switch, HIGH);
      digitalWrite(gen_line_output, HIGH);
      Serial.println("GEN is ON  ");
      lcd.setCursor (0,0); // set to line 1, char 0  
      lcd.print("GEN is on           ");
    }
      }
    }
  


bool checkGenState() {
  // TODO - check water level and oil level
  return true;
}
